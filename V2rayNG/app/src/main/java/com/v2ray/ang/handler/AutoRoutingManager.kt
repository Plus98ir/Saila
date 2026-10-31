package com.v2ray.ang.handler

import android.content.Context
import android.telephony.TelephonyManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Saila: picks the routing preset from the user's own country. Iran → Iranian sites/IPs direct (and
 * Iranian apps direct), Russia → Russian whitelist, China → Chinese whitelist, elsewhere → all
 * through the VPN. Applied only when the detected country changes, so manual edits in between stay.
 */
object AutoRoutingManager {

    fun isEnabled() = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_ROUTING, true)

    /** Country the current preset was applied for (lowercase ISO code), or null. */
    fun appliedCountry(): String? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_AUTO_ROUTING_COUNTRY)?.takeIf { it.isNotEmpty() }

    fun setEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_ROUTING, enabled)
        // Forget the last country so turning it back on re-applies the preset.
        if (!enabled) MmkvManager.encodeSettings(AppConfig.PREF_AUTO_ROUTING_COUNTRY, "")
    }

    /** Detects the country and applies its preset if it changed. Returns true when the routing changed. */
    suspend fun check(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext false
        val detected = detectCountry(context) ?: return@withContext false
        val cc = detected.country
        if (cc == appliedCountry()) return@withContext false

        SettingsManager.resetRoutingRulesetsFromPresets(context, presetFor(cc))
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_ROUTING_COUNTRY, cc)
        if (cc == "ir" && !MmkvManager.decodeSettingsBool(AppConfig.PREF_IRAN_APPS_DIRECT, false)) {
            MmkvManager.encodeSettings(AppConfig.PREF_IRAN_APPS_DIRECT, true)
            IranAppDetector.scanAndStore(context)
        }
        LogUtil.i(AppConfig.TAG, "AutoRouting: country $cc (from ${detected.source}) → ${presetFor(cc)}")
        true
    }

    internal fun presetFor(cc: String) = when (cc) {
        "ir" -> RoutingType.WHITE_IRAN
        "ru" -> RoutingType.WHITE_RUSSIA
        "cn" -> RoutingType.WHITE
        else -> RoutingType.GLOBAL
    }

    /** A detected country (lowercase ISO code) and where it came from: "network", "ip" or "sim". */
    data class Detected(val country: String, val source: String)

    private fun detectCountry(context: Context): Detected? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return pickCountry(tm?.networkCountryIso, tm?.simCountryIso, ::lookupIp)
    }

    /**
     * The cell tower's country first: a VPN, a tunnelled Wi-Fi or a VPN restart can make the IP look
     * foreign (0.1.4-b2 put a user in Iran on Germany), the tower cannot. The IP covers phones with no
     * mobile network; the SIM is last since it stays home when travelling. The phone's region setting
     * is never used, it says nothing about where the phone is.
     */
    internal fun pickCountry(network: String?, sim: String?, ip: () -> String?): Detected? {
        isoOrNull(network)?.let { return Detected(it, "network") }
        isoOrNull(ip())?.let { return Detected(it, "ip") }
        isoOrNull(sim)?.let { return Detected(it, "sim") }
        return null
    }

    private fun isoOrNull(code: String?) = code?.trim()?.lowercase()?.takeIf { it.length == 2 }

    private fun lookupIp(): String? {
        val sources = listOf(
            "https://ipwho.is/?fields=success,country_code" to "country_code",
            "http://ip-api.com/json/?fields=countryCode" to "countryCode",
        )
        for ((url, field) in sources) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val cc = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString(field)
                if (cc.length == 2) return cc.lowercase()
            }
        }
        return null
    }
}
