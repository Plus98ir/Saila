package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.ApplicationInfo
import com.v2ray.ang.AppConfig

/**
 * Saila: finds installed Iranian apps (banks, Divar, Snapp, Bale, Eitaa, operators, ...) so they can
 * go direct instead of through the VPN; many of them refuse to work from a foreign IP.
 */
object IranAppDetector {
    /** Well-known Iranian apps and Iranian banking vendors whose package does not start with "ir.". */
    private val KNOWN_PREFIXES = listOf(
        "cab.snapp", "taxi.tapsi", "com.digikala", "com.aparat", "com.filimo", "mobi.mmdt",
        "com.myirancell", "com.mydigipay", "com.sibche", "com.cafebazaar", "com.farsitel.bazaar",
        "com.samanpr.", "com.dotin.", "com.tosan.", "com.sadadpsp.",
        "ir.", "org.ir.", "com.ir.",
    )

    /**
     * Foreign apps that are often shown with a Persian name (or ship Persian forks) but are blocked
     * in Iran; sending them direct would break them, so the name check skips them.
     */
    private val FOREIGN_PREFIXES = listOf(
        "org.telegram.", "org.thunderdog.", "com.whatsapp", "com.instagram.", "com.facebook.",
        "com.twitter.", "com.google.", "com.android.", "com.zhiliaoapp.", "com.ss.android.",
        "com.snapchat.", "com.discord", "com.spotify.", "com.netflix.", "com.microsoft.",
    )

    /**
     * Any Persian/Arabic-script letter. Bank apps such as Blu (بلو) or Mellat (ملت) use none of the
     * Persian-only letters, so checking only پ چ ژ گ missed them.
     */
    private val PERSIAN_SCRIPT = Regex("[ء-يپچژکگی]")

    internal fun isIranianPackage(pkg: String) =
        KNOWN_PREFIXES.any { pkg.startsWith(it) } || pkg.endsWith(".ir") || pkg.contains(".ir.")

    /** True when the app should go direct, judged by its package and (when known) its name. */
    internal fun isIranian(pkg: String, label: String?): Boolean {
        if (isIranianPackage(pkg)) return true
        if (label.isNullOrEmpty() || FOREIGN_PREFIXES.any { pkg.startsWith(it) }) return false
        return PERSIAN_SCRIPT.containsMatchIn(label)
    }

    /**
     * Full scan including app names; slower, run it off the main thread. The result is stored so
     * the VPN service can reuse it.
     */
    fun scanAndStore(context: Context): Set<String> {
        val pm = context.packageManager
        val found = pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { info ->
                isIranian(info.packageName, runCatching { info.loadLabel(pm).toString() }.getOrNull())
            }
            .map { it.packageName }
            .filter { it != context.packageName }
            .toSet()
        MmkvManager.encodeSettings(AppConfig.PREF_IRAN_APPS, found.toMutableSet())
        return found
    }

    /**
     * Re-scans when the option is on, so apps installed since the last scan (or missed by an older
     * version of the rules) go direct. Off the main thread. Returns true when the stored list changed.
     */
    fun refreshIfEnabled(context: Context): Boolean {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_IRAN_APPS_DIRECT, false)) return false
        val before = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_IRAN_APPS).orEmpty().toSet()
        return scanAndStore(context) != before
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
