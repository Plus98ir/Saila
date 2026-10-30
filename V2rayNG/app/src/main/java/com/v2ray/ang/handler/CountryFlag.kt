package com.v2ray.ang.handler

import com.v2ray.ang.util.JsonUtil
import android.util.Log
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Saila: country of a server for its flag. Order: a flag emoji in the name, a country code as the
 * first word ("DE WS") or a country name anywhere (English or Persian), and finally the server
 * address looked up on ip-api.com (cached). CDN addresses (Cloudflare etc.) have no real country.
 */
object CountryFlag {
    const val CDN = "CDN"
    private const val CACHE_PREFIX = "saila_geo_"
    private val FLAG_EMOJI = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")
    private val CDN_ISPS = listOf("cloudflare", "akamai", "fastly", "cloudfront", "gcore", "arvan", "derak")

    private val isoCodes: Set<String> by lazy { Locale.getISOCountries().toSet() }

    /** Country names (English and Persian, lowercased) → ISO code, longest names first. */
    private val countryNames: List<Pair<String, String>> by lazy {
        val fa = Locale.forLanguageTag("fa")
        Locale.getISOCountries().flatMap { cc ->
            val loc = Locale("", cc)
            listOf(loc.getDisplayCountry(Locale.ENGLISH), loc.getDisplayCountry(fa))
                .filter { it.length >= 4 }
                .map { it.lowercase() to cc }
        }.sortedByDescending { it.first.length }
    }

    fun emojiOf(cc: String): String = cc.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

    fun flagInName(remarks: String): String? = FLAG_EMOJI.find(remarks)?.value

    fun stripFlag(remarks: String): String = FLAG_EMOJI.replace(remarks, "").trim()

    /** Country code from the name alone, or null. */
    fun codeFromName(remarks: String): String? {
        val name = stripFlag(remarks)
        val first = name.split(' ', '-', '_', '|', '·').firstOrNull { it.isNotBlank() }.orEmpty()
        if (first.length == 2 && first == first.uppercase() && first in isoCodes) return first
        val lower = name.lowercase()
        return countryNames.firstOrNull { lower.contains(it.first) }?.second
    }

    // ---- address lookup, batched and cached ----

    private val _resolved = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolved: StateFlow<Map<String, String>> = _resolved.asStateFlow()
    private val pending = LinkedHashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batchJob: Job? = null

    /** Country code (or [CDN]) for a host if known; otherwise queues a lookup and returns null. */
    fun lookup(host: String): String? {
        if (host.isBlank()) return null
        _resolved.value[host]?.let { return it }
        MmkvManager.decodeSettingsString(CACHE_PREFIX + host)?.takeIf { it.isNotBlank() }?.let { cached ->
            _resolved.update { it + (host to cached) }
            return cached
        }
        synchronized(pending) { pending.add(host) }
        if (batchJob?.isActive != true) {
            batchJob = scope.launch {
                delay(400)
                val hosts = synchronized(pending) { pending.take(100).also { pending.removeAll(it.toSet()) } }
                if (hosts.isNotEmpty()) fetch(hosts)
            }
        }
        return null
    }

    private data class GeoAnswer(val query: String? = null, val countryCode: String? = null, val isp: String? = null, val org: String? = null)
    private data class WhoConnection(val isp: String? = null, val org: String? = null)
    private data class WhoAnswer(val success: Boolean = false, val country_code: String? = null, val connection: WhoConnection? = null)

    /** Cloudflare's published IPv4 ranges, so its CDN addresses need no lookup at all. */
    private val CLOUDFLARE_V4 = listOf(
        "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22", "141.101.64.0/18",
        "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20", "197.234.240.0/22", "198.41.128.0/17",
        "162.158.0.0/15", "104.16.0.0/13", "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
    ).map { cidr ->
        val (ip, bits) = cidr.split("/")
        ipToLong(ip) to bits.toInt()
    }

    private fun ipToLong(ip: String): Long =
        ip.split(".").fold(0L) { acc, part -> (acc shl 8) or (part.toLongOrNull() ?: 0L) }

    private fun isCloudflare(ip: String): Boolean {
        if (!ip.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) return false
        val v = ipToLong(ip)
        return CLOUDFLARE_V4.any { (net, bits) ->
            val mask = if (bits == 0) 0L else (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
            (v and mask) == (net and mask)
        }
    }

    private fun codeFor(countryCode: String?, isp: String?, org: String?): String? {
        val provider = "$isp $org".lowercase()
        return when {
            CDN_ISPS.any { provider.contains(it) } -> CDN
            !countryCode.isNullOrBlank() -> countryCode.uppercase()
            else -> null
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /** ipwho.is (HTTPS) first, ip-api.com batch as fallback; some Iranian DNS blocks ip-api. */
    private fun fetch(hosts: List<String>) {
        val found = HashMap<String, String>()
        // Domains are resolved first: both services want IP addresses.
        val ips = hosts.associateWith { host -> runCatching { InetAddress.getByName(host).hostAddress }.getOrNull() ?: host }
        ips.forEach { (host, ip) -> if (isCloudflare(ip)) found[host] = CDN }

        val rest = hosts.filter { it !in found }
        var failed = rest
        if (rest.isNotEmpty()) {
            failed = rest.filter { host ->
                val code = runCatching {
                    val a = JsonUtil.fromJsonSafe(httpGet("https://ipwho.is/${ips[host]}?fields=success,country_code,connection"), WhoAnswer::class.java)
                    if (a?.success == true) codeFor(a.country_code, a.connection?.isp, a.connection?.org) else null
                }.getOrNull()
                if (code != null) found[host] = code
                code == null
            }
        }
        if (failed.isNotEmpty()) {
            try {
                val body = failed.joinToString(",", "[", "]") { "{\"query\":\"${ips[it]}\",\"fields\":\"query,countryCode,isp,org\"}" }
                val conn = URL("http://ip-api.com/batch").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                // Answers come back in request order.
                JsonUtil.fromJsonSafe(text, Array<GeoAnswer>::class.java).orEmpty().forEachIndexed { i, a ->
                    val host = failed.getOrNull(i) ?: return@forEachIndexed
                    codeFor(a.countryCode, a.isp, a.org)?.let { found[host] = it }
                }
            } catch (e: Exception) {
                Log.w("SailaGeo", "ip-api fallback failed: $e")
            }
        }
        found.forEach { (host, code) -> MmkvManager.encodeSettings(CACHE_PREFIX + host, code) }
        Log.i("SailaGeo", "Resolved ${found.size}/${hosts.size}")
        _resolved.update { it + found }
    }
}
