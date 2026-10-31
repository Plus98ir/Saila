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
import java.util.Locale

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
        val cc = detectCountry(context) ?: return@withContext false
        if (cc == appliedCountry()) return@withContext false

        SettingsManager.resetRoutingRulesetsFromPresets(context, presetFor(cc))
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_ROUTING_COUNTRY, cc)
        if (cc == "ir" && !MmkvManager.decodeSettingsBool(AppConfig.PREF_IRAN_APPS_DIRECT, false)) {
            MmkvManager.encodeSettings(AppConfig.PREF_IRAN_APPS_DIRECT, true)
            IranAppDetector.scanAndStore(context)
        }
        LogUtil.i(AppConfig.TAG, "AutoRouting: country $cc → ${presetFor(cc)}")
        true
    }

    internal fun presetFor(cc: String) = when (cc) {
        "ir" -> RoutingType.WHITE_IRAN
        "ru" -> RoutingType.WHITE_RUSSIA
        "cn" -> RoutingType.WHITE
        else -> RoutingType.GLOBAL
    }

    /** The user's own IP first (the app itself bypasses the VPN), then the mobile network, SIM, locale. */
    private fun detectCountry(context: Context): String? {
        lookupIp()?.let { return it }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        listOf(tm?.networkCountryIso, tm?.simCountryIso).forEach { iso ->
            if (!iso.isNullOrBlank() && iso.length == 2) return iso.lowercase()
        }
        return Locale.getDefault().country.lowercase().takeIf { it.length == 2 }
    }

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
