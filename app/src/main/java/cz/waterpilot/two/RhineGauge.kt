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
