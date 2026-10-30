package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.ApplicationInfo
import com.v2ray.ang.AppConfig

/**
 * Saila: finds installed Iranian apps (banks, Divar, Snapp, Bale, Eitaa, operators, ...) so they can
 * go direct instead of through the VPN; many of them refuse to work from a foreign IP.
 */
object IranAppDetector {
    /** Well-known Iranian apps whose package does not start with "ir.". */
    private val KNOWN_PREFIXES = listOf(
        "cab.snapp", "taxi.tapsi", "com.digikala", "com.aparat", "com.filimo", "mobi.mmdt",
        "com.myirancell", "com.mydigipay", "com.sibche", "com.cafebazaar", "com.farsitel.bazaar",
        "ir.", "org.ir.", "com.ir.",
    )

    /** Letters that only Persian uses (پ چ ژ گ), so an app named with them is Iranian. */
    private val PERSIAN_LETTERS = Regex("[پچژگ]")

    private fun isIranianPackage(pkg: String) =
        KNOWN_PREFIXES.any { pkg.startsWith(it) } || pkg.endsWith(".ir") || pkg.contains(".ir.")

    /**
     * Full scan including app names; slower, run it off the main thread when the user turns the
     * option on. The result is stored so the VPN service can reuse it.
     */
    fun scanAndStore(context: Context): Set<String> {
        val pm = context.packageManager
        val found = pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { info ->
                isIranianPackage(info.packageName) ||
                    runCatching { PERSIAN_LETTERS.containsMatchIn(info.loadLabel(pm).toString()) }.getOrDefault(false)
            }
            .map { it.packageName }
            .filter { it != context.packageName }
            .toSet()
        MmkvManager.encodeSettings(AppConfig.PREF_IRAN_APPS, found.toMutableSet())
        return found
    }

    /** Quick list for VPN start: the stored scan plus any newly installed app matched by package. */
    fun directApps(context: Context): Set<String> {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_IRAN_APPS_DIRECT, false)) return emptySet()
        val installed = context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
        val stored = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_IRAN_APPS).orEmpty()
        return (stored.filter { it in installed } + installed.filter { isIranianPackage(it) })
            .filter { it != context.packageName }
            .toSet()
    }
}
