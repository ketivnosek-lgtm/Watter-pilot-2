package cz.waterpilot.two

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

// Official PEGELONLINE readings; no synthetic fallback.
internal data class RhineGauge(val centimeters: String, val measuredAt: String)

internal fun fetchDuisburgGauge(): RhineGauge? {
    val base = "https://www.pegelonline.wsv.de/webservices/rest-api/v2"
    val stations = readGaugeJson("$base/stations.json?fuzzyId=DUISBURG-RUHRORT&includeTimeseries=true&includeCurrentMeasurement=true")
    val list = JSONArray(stations)
    for (i in 0 until list.length()) {
        val station = list.getJSONObject(i)
        val name = station.optString("shortname")
        if (!name.equals("DUISBURG-RUHRORT", ignoreCase = true)) continue
        val series = station.optJSONArray("timeseries") ?: continue
        for (j in 0 until series.length()) {
            val item = series.getJSONObject(j)
            if (item.optString("shortname") != "W") continue
            val measurement = item.optJSONObject("currentMeasurement") ?: continue
            val value = measurement.optDouble("value", Double.NaN)
            val timestamp = measurement.optString("timestamp")
            if (!value.isFinite() || timestamp.isBlank()) return null
            val unit = item.optString("unit")
            val cm = when (unit.lowercase()) {
                "cm" -> value
                "m" -> value * 100.0
                else -> return null
            }
            return RhineGauge(cm.toInt().toString() + " cm", timestamp)
        }
    }
    return null
}

private fun readGaugeJson(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.setRequestProperty("Accept", "application/json")
    try {
        if (connection.responseCode !in 200..299) error("Gauge HTTP ${connection.responseCode}")
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}


/** Optional official PEGELONLINE forecast; never substitute measured readings. */
internal data class RhineForecast(val tomorrow: String, val dayAfter: String, val source: String = "PEGELONLINE WV")

internal fun fetchDuisburgForecast(): RhineForecast? {
    return runCatching { fetchElwisForecast() }.getOrNull() ?: fetchPegelForecast()
}

private fun fetchPegelForecast(): RhineForecast? {
    val base = "https://www.pegelonline.wsv.de/webservices/rest-api/v2"
    // Only stations explicitly publishing a WV series are eligible.
    val stations = JSONArray(readGaugeJson("$base/stations.json?includeForecastTimeseries=true&hasTimeseries=WV"))
    var uuid: String? = null
    var unit = "cm"
    for (i in 0 until stations.length()) {
        val station = stations.getJSONObject(i)
        if (!station.optString("shortname").equals("DUISBURG-RUHRORT", true)) continue
        uuid = station.optString("uuid").takeIf { it.isNotBlank() }
        val series = station.optJSONArray("timeseries")
        if (series != null) for (j in 0 until series.length()) {
            val item = series.getJSONObject(j)
            if (item.optString("shortname") == "WV") unit = item.optString("unit", "cm")
        }
        break
    }
    val stationId = uuid ?: return null
    val data = JSONArray(readGaugeJson("$base/stations/$stationId/WV/measurements.json"))
    val zone = java.time.ZoneId.of("Europe/Berlin")
    val today = java.time.LocalDate.now(zone)
    val values = mutableMapOf<java.time.LocalDate, Pair<Long, Double>>()
    for (i in 0 until data.length()) {
        val item = data.getJSONObject(i)
        val timestamp = item.optString("timestamp")
        val value = item.optDouble("value", Double.NaN)
        if (!value.isFinite()) continue
        val moment = runCatching { java.time.OffsetDateTime.parse(timestamp).atZoneSameInstant(zone) }.getOrNull() ?: continue
        val date = moment.toLocalDate()
        if (date != today.plusDays(1) && date != today.plusDays(2)) continue
        // Choose the published value nearest to 13:00 local time for each day.
        val distance = kotlin.math.abs(moment.toLocalTime().toSecondOfDay() - 13 * 3600).toLong()
        if (distance < (values[date]?.first ?: Long.MAX_VALUE)) values[date] = distance to value
    }
    fun format(date: java.time.LocalDate): String {
        val v = values[date]?.second ?: return "— cm"
        val cm = when (unit.lowercase(java.util.Locale.ROOT)) {
            "cm" -> v
            "m" -> v * 100
            else -> return "— cm"
        }
        return "${kotlin.math.round(cm).toInt()} cm"
    }
    val tomorrow = format(today.plusDays(1))
    val dayAfter = format(today.plusDays(2))
    return if (tomorrow == "— cm" && dayAfter == "— cm") null else RhineForecast(tomorrow, dayAfter)
}


/** ELWIS short-range table: each dated short-range column has value and change.
 * Reject unexpected table layouts instead of interpreting long-range bounds as values.
 */
private fun fetchElwisForecast(): RhineForecast? {
    val connection = URL("https://www.elwis.de/DE/dynamisch/Wasserstaende/Pegelvorhersage%3ADUISBURG-RUHRORT").openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    val html = try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally { connection.disconnect() }
    return parseElwisForecast(html, java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin")))
}

internal fun parseElwisForecast(html: String, today: java.time.LocalDate): RhineForecast? {
    val rows = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>").findAll(html).map { row ->
        Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>").findAll(row.groupValues[1]).map {
            android.text.Html.fromHtml(it.groupValues[1], android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
        }.toList()
    }.toList()
    val header = rows.indexOfFirst { cells -> cells.any { it == "Uhr" } && cells.any { Regex("\\d{2}\\.\\d{2}\\.\\d{4}").containsMatchIn(it) } }
    if (header < 0) return null
    val dates = rows[header].mapNotNull { Regex("\\d{2}\\.\\d{2}\\.\\d{4}").find(it)?.value }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")
    val targets = listOf(today.plusDays(1), today.plusDays(2))
    val row = rows.drop(header + 1).firstOrNull { it.firstOrNull() == "13:00:00" } ?: return null
    // Verified ELWIS short-range layout: two dates followed by long-range columns.
    fun value(date: java.time.LocalDate): String {
        val index = dates.indexOf(date.format(fmt))
        if (index !in 0..1) return "— cm"
        val cell = row.getOrNull(1 + index * 2) ?: return "— cm"
        val change = row.getOrNull(2 + index * 2) ?: return "— cm"
        if (!change.startsWith("(") || !change.endsWith(")")) return "— cm"
        val cm = cell.toIntOrNull() ?: return "— cm"
        return "$cm cm"
    }
    val a = value(targets[0])
    val b = value(targets[1])
    return if (a == "— cm" && b == "— cm") null else RhineForecast(a, b, "ELWIS / BfG")
}
