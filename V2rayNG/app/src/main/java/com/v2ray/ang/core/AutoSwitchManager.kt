package com.v2ray.ang.core

import android.app.Service
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Saila auto-switch: while connected, checks the running server through the tunnel every
 * [CHECK_INTERVAL_MS]. After [FAILURES_BEFORE_SWITCH] failed checks in a row it moves to the reserve
 * server: the one the user picked, or by default the lowest-ping server, which is measured in the
 * background every [RESERVE_REFRESH_TICKS] checks so the switch is immediate. If the reserve does
 * not work either, all servers of the subscription are tested and the fastest working one wins.
 * If none works (no internet at all), it stays put.
 */
object AutoSwitchManager {
    const val RESERVE_AUTO = "auto"

    private const val CHECK_INTERVAL_MS = 10_000L
    private const val FAILURES_BEFORE_SWITCH = 2
    private const val RESERVE_REFRESH_TICKS = 30
    private const val CANDIDATE_TIMEOUT_MS = 10_000L
    private const val MAX_CANDIDATES = 24

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var autoReserve: String? = null

    fun isEnabled() = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SWITCH, true)

    /** The reserve the user picked by hand, or null when it is automatic (lowest ping). */
    private fun manualReserve(): String? = MmkvManager.decodeSettingsString(AppConfig.PREF_RESERVE_SERVER)
        ?.takeIf { it.isNotBlank() && it != RESERVE_AUTO && MmkvManager.decodeServerConfig(it) != null }

    fun start(service: Service) {
        stop()
        autoReserve = null
        scope.launch {
            var failures = 0
            var tick = 0
            while (isActive) {
                kotlinx.coroutines.delay(CHECK_INTERVAL_MS)
                if (!isEnabled() || !CoreServiceManager.isRunning() || CoreServiceManager.isReloadingNow()) {
                    failures = 0
                    continue
                }
                val ok = CoreServiceManager.measureCurrentDelay() >= 0
                failures = if (ok) 0 else failures + 1

                // Keep the automatic reserve fresh while the connection is fine.
                if (ok && manualReserve() == null && tick++ % RESERVE_REFRESH_TICKS == 0) {
                    autoReserve = findHealthyServer(service)
                }
                if (failures < FAILURES_BEFORE_SWITCH) continue
                failures = 0

                val current = MmkvManager.getSelectServer()
                val reserve = manualReserve() ?: autoReserve
                val best = reserve?.takeIf { it != current && testServer(service, it) >= 0 }
                    ?: findHealthyServer(service)
                    ?: continue
                if (!isActive || !CoreServiceManager.isRunning()) break
                autoReserve = null
                tick = 0
                val name = MmkvManager.decodeServerConfig(best)?.remarks.orEmpty()
                LogUtil.i(AppConfig.TAG, "AutoSwitch: switching to $name")
                MmkvManager.setSelectServer(best)
                if (CoreServiceManager.reloadCore()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            service,
                            service.getString(R.string.saila_switched_to, name),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    fun stop() {
        scope.coroutineContext.cancelChildren()
    }

    private fun testServer(service: Service, guid: String): Long = runCatching {
        val config = CoreConfigManager.getV2rayConfig4Speedtest(service, guid)
        if (config.status) CoreNativeManager.measureOutboundDelay(config.content, SettingsManager.getDelayTestUrl()) else -1L
    }.getOrDefault(-1L)

    /** Real-delay test of the other servers in the current subscription; lowest ping wins. */
    private suspend fun findHealthyServer(service: Service): String? {
        val current = MmkvManager.getSelectServer() ?: return null
        val subId = MmkvManager.decodeServerConfig(current)?.subscriptionId ?: return null
        val all = MmkvManager.decodeServerList(subId)
        val index = all.indexOf(current)
        // Start with the servers after the current one so a list of equal servers rotates.
        val candidates = (all.drop(index + 1) + all.take(index.coerceAtLeast(0)))
            .filter { it != current }
            .take(MAX_CANDIDATES)
        if (candidates.isEmpty()) return null

        val results = withContext(Dispatchers.IO) {
            candidates.map { guid ->
                async {
                    val delay = withTimeoutOrNull(CANDIDATE_TIMEOUT_MS) { testServer(service, guid) } ?: -1L
                    guid to delay
                }
            }.awaitAll()
        }
        return results.filter { it.second >= 0 }.minByOrNull { it.second }?.first
    }
}
