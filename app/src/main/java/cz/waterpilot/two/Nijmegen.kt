package cz.waterpilot.two

import java.net.HttpURLConnection
import java.net.URL

/** RhinePlanner planning estimate. This is NOT official MGD. */
data class NijmegenEstimate(val waterCm: String, val loadingCm: String)

fun fetchNijmegenEstimate(): NijmegenEstimate? {
    val conn = URL("https://rhineplanner.com/waterstanden/nijmegen").openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    conn.setRequestProperty("User-Agent", "WaterPilot2/0.1")
    try {
        if (conn.responseCode != 200) return null
        val html = conn.inputStream.bufferedReader().use { it.readText() }
        val text = html.replace(Regex("<script\\b[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style\\b[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
        val water = Regex("Waterstand\\s+(\\d{2,4})\\s*cm", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val loading = Regex("Aflaaddiepte\\s+(\\d{2,4})\\s*cm", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        return if (water != null && loading != null) NijmegenEstimate("$water cm", "$loading cm") else null
    } finally { conn.disconnect() }
}
