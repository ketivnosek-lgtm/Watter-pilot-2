package cz.waterpilot.two

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal data class NlPoint(val time: Instant, val cm: Double)
internal data class NlWater(val station: String, val level: NlPoint?, val high: NlPoint?, val low: NlPoint?, val loaded: Instant)
private val nlZone = ZoneId.of("Europe/Amsterdam")
internal fun nlTime(t: Instant) = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(nlZone).format(t)
internal fun nlHeight(p: NlPoint?) = p?.let { String.format(Locale.US, "%+.2f m", it.cm / 100) } ?: "— m"
internal fun nlTide(p: NlPoint?) = p?.let { nlTime(it.time) + " / " + nlHeight(it) } ?: "—"

private fun code(v: String) = JSONObject().put("Code", v)
private fun metadata(process: String) = JSONObject().put("Grootheid", code("WATHTE")).put("Hoedanigheid", code("NAP")).put("ProcesType", process)
private fun requestNl(station: String, meta: JSONObject, start: Instant, end: Instant): List<JSONObject> {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(nlZone)
    val payload = JSONObject().put("Locatie", code(station))
        .put("AquoPlusWaarnemingMetadata", JSONObject().put("AquoMetadata", meta))
        .put("Periode", JSONObject().put("Begindatumtijd", fmt.format(start)).put("Einddatumtijd", fmt.format(end)))
    val c = URL("https://ddapi20-waterwebservices.rijkswaterstaat.nl/ONLINEWAARNEMINGENSERVICES/OphalenWaarnemingen").openConnection() as HttpURLConnection
    return try {
        c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 12000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val obj = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        if (!obj.optBoolean("Succesvol")) return emptyList()
        val arr = obj.optJSONArray("WaarnemingenLijst") ?: return emptyList()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optJSONObject("Locatie")?.optString("Code")?.equals(station, true) == true }
    } catch (_: Exception) { emptyList() } finally { c.disconnect() }
}
private fun rows(series: JSONObject): List<JSONObject> {
    val a = series.optJSONArray("MetingenLijst") ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}
private fun time(row: JSONObject): Instant? = runCatching { OffsetDateTime.parse(row.getString("Tijdstip")).toInstant() }.getOrNull()
private fun points(series: List<JSONObject>, process: String): List<NlPoint> = series.flatMap { s ->
    val m = s.optJSONObject("AquoMetadata")
    if (m?.optJSONObject("Eenheid")?.optString("Code") != "cm" ||
        m.optJSONObject("Hoedanigheid")?.optString("Code") != "NAP" ||
        m.optJSONObject("Grootheid")?.optString("Code") != "WATHTE" ||
        m.optString("ProcesType") != process) emptyList()
    else rows(s).mapNotNull { row ->
        val t = time(row)
        val v = row.optJSONObject("Meetwaarde")?.optDouble("Waarde_Numeriek") ?: Double.NaN
        if (t != null && v.isFinite() && v in -1000.0..1000.0) NlPoint(t, v) else null
    }
}
internal fun fetchNlWater(page: Int): NlWater? {
    val stations = when(page) {
        1 -> listOf("rotterdam.nieuwemaas.boompjes", "rotterdam.lekhaven", "rotterdam.eemhaven")
        2 -> listOf("hansweert")
        3 -> listOf("terneuzen")
        4 -> listOf("yerseke")
        else -> return null
    }
    val now = Instant.now()
    val start = now.minus(3, ChronoUnit.HOURS)
    val end = now.plus(36, ChronoUnit.HOURS)
    var best: NlWater? = null
    fun completeness(w: NlWater) = listOf(w.level, w.high, w.low).count { it != null }
    for (station in stations) {
        val level = points(requestNl(station, metadata("meting"), start, now), "meting").filter { !it.time.isAfter(now) }.maxByOrNull { it.time }
        val extremes = requestNl(station, JSONObject().put("Groepering", code("GETETBRKD2")), now, end)
        val heights = points(extremes, "astronomisch").associateBy { it.time }
        val types = extremes.filter { it.optJSONObject("AquoMetadata")?.optJSONObject("Typering")?.optString("Code") == "GETETTPE" }
            .flatMap { rows(it) }.mapNotNull { row -> time(row)?.let { it to row.optJSONObject("Meetwaarde")?.optString("Waarde_Alfanumeriek") } }
        fun next(kind: String) = types.filter { it.second == kind && it.first.isAfter(now) }.mapNotNull { heights[it.first] }.minByOrNull { it.time }
        var high = next("hoogwater"); var low = next("laagwater")
        if (high == null || low == null) {
            val curve = points(requestNl(station, metadata("astronomisch"), start, end), "astronomisch").distinctBy { it.time }.sortedBy { it.time }
            val peaks = curve.windowed(3).filter { it[1].time.isAfter(now) && it[2].time.epochSecond - it[0].time.epochSecond <= 1800 }
            if (high == null) high = peaks.firstOrNull { it[1].cm > it[0].cm && it[1].cm >= it[2].cm }?.get(1)
            if (low == null) low = peaks.firstOrNull { it[1].cm < it[0].cm && it[1].cm <= it[2].cm }?.get(1)
        }
        val candidate = NlWater(station, level, high, low, Instant.now())
        val score = completeness(candidate)
        if (score == 3) return candidate
        if (score > (best?.let { completeness(it) } ?: 0)) best = candidate
    }
    return best
}
