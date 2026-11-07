package cz.waterpilot.two

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherNow(
    val temperature: String,
    val windBft: String,
    val direction: String,
    val visibility: String,
    val description: String
)

private val coordinates = listOf(
    51.4344 to 6.7623, 51.9225 to 4.4792, 51.4467 to 4.0067,
    51.3356 to 3.8278, 51.4925 to 4.0500
)

fun fetchWeather(index: Int): WeatherNow? {
    if (index !in coordinates.indices) return null
    val (lat, lon) = coordinates[index]
    val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,wind_speed_10m,wind_direction_10m,weather_code&hourly=visibility&forecast_days=1&timezone=Europe%2FAmsterdam"
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    try {
        if (connection.responseCode != 200) return null
        val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val current = root.getJSONObject("current")
        val kmh = current.getDouble("wind_speed_10m")
        val bft = when {
            kmh < 1 -> 0; kmh < 6 -> 1; kmh < 12 -> 2; kmh < 20 -> 3
            kmh < 29 -> 4; kmh < 39 -> 5; kmh < 50 -> 6; kmh < 62 -> 7
            kmh < 75 -> 8; kmh < 89 -> 9; kmh < 103 -> 10; kmh < 118 -> 11
            else -> 12
        }
        val directions = listOf("N – North", "NE – Northeast", "E – East", "SE – Southeast", "S – South", "SW – Southwest", "W – West", "NW – Northwest")
        val degrees = current.getDouble("wind_direction_10m")
        val direction = directions[((degrees + 22.5).toInt() / 45) % 8]
        val code = current.getInt("weather_code")
        val description = when (code) {
            0 -> "Jasno"; 1, 2 -> "Polojasno"; 3 -> "Zataženo"
            45, 48 -> "Mlha"; 51, 53, 55, 56, 57 -> "Mrholení"
            61, 63, 65, 66, 67, 80, 81, 82 -> "Déšť"
            71, 73, 75, 77, 85, 86 -> "Sněžení"
            95, 96, 99 -> "Bouřka"; else -> "Neznámé"
        }
        val hourly = root.optJSONObject("hourly")
        val visibility = hourly?.optJSONArray("visibility")?.optDouble(0, Double.NaN)
        return WeatherNow(
            String.format(Locale.US, "%.0f °C", current.getDouble("temperature_2m")),
            "$bft Bft", direction,
            if (visibility != null && !visibility.isNaN()) String.format(Locale.US, "%.1f km", visibility / 1000.0) else "—",
            description
        )
    } finally {
        connection.disconnect()
    }
}
