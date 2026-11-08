package cz.waterpilot.two

/** Official first-choice sources for automatic data readers and manual verification.
 *  Prefer structured data endpoints over scraping HTML pages.
 *  Do not treat website availability as proof of a successful measurement fetch.
 */
internal data class WaterSource(val location: String, val provider: String, val website: String, val dataService: String? = null)

internal val DEFAULT_WATER_SOURCES = listOf(
    WaterSource("Duisburg", "WSV PEGELONLINE", "https://www.pegelonline.wsv.de/", "https://www.pegelonline.wsv.de/webservices/rest-api/v2/"),
    WaterSource("Duisburg forecast", "ELWIS / BfG", "https://www.elwis.de/DE/dynamisch/Wasserstaende/Pegelvorhersage%3ADUISBURG-RUHRORT"),
    WaterSource("Rotterdam", "Rijkswaterstaat Waterinfo", "https://waterinfo.rws.nl/", "https://ddapi20-waterwebservices.rijkswaterstaat.nl/"),
    WaterSource("Hansweert", "Rijkswaterstaat Waterinfo", "https://waterinfo.rws.nl/", "https://ddapi20-waterwebservices.rijkswaterstaat.nl/"),
    WaterSource("Terneuzen", "Rijkswaterstaat Waterinfo", "https://waterinfo.rws.nl/", "https://ddapi20-waterwebservices.rijkswaterstaat.nl/"),
    WaterSource("Yerseke", "Rijkswaterstaat Waterinfo", "https://waterinfo.rws.nl/", "https://ddapi20-waterwebservices.rijkswaterstaat.nl/"),
    WaterSource("Nijmegen MGD", "Rijkswaterstaat Vaarweginformatie", "https://www.vaarweginformatie.nl/")
)

// Rijkswaterstaat location codes must be resolved through OphalenCatalogus
// before querying OphalenWaarnemingen; never guess station IDs.
// Astronomical tides are not weather-adjusted predictions.
// MGD must never be inferred from gauge readings or RhinePlanner estimates.


/**
 * Source priority per value, not per whole screen. A source can provide water level
 * without providing tides. Fallbacks must be verified for access and matching datum.
 */
internal data class SourcePriority(val metric: String, val providers: List<String>)

internal val WATER_SOURCE_PRIORITIES = listOf(
    // Keep the already working official live gauge reader unchanged.
    SourcePriority("Duisburg.currentLevel", listOf("WSV PEGELONLINE REST", "ELWIS current gauge (integration pending)")),
    SourcePriority("Duisburg.forecast", listOf("ELWIS / BfG official forecast (integration pending)", "WSV PEGELONLINE WV when published")),
    SourcePriority("Duisburg.wind", listOf("Open-Meteo (working)", "DWD open weather data (integration pending)")),
    SourcePriority("Duisburg.visibility", listOf("Open-Meteo (working)", "DWD observations (integration pending)")),
    // Independent fallback must use the SAME NAP datum and the same observation type.
    SourcePriority("Rotterdam.currentLevel", listOf("Rijkswaterstaat DDAPI 2.0", "Rijkswaterstaat WFS", "Port of Rotterdam Weather & Tide (access verification pending)")),
    SourcePriority("Rotterdam.highLow", listOf("Rijkswaterstaat DDAPI 2.0 weather-adjusted tide", "Rijkswaterstaat astronomical tide (explicitly labeled)", "Port of Rotterdam Weather & Tide (access verification pending)")),
    SourcePriority("Hansweert.currentLevel", listOf("Rijkswaterstaat DDAPI 2.0", "Rijkswaterstaat WFS", "Waterinfo Vlaanderen (availability and datum verification pending)")),
    SourcePriority("Hansweert.highLow", listOf("Rijkswaterstaat DDAPI 2.0", "Waterinfo Vlaanderen verified tide prediction", "Rijkswaterstaat astronomical tide (explicitly labeled)")),
    SourcePriority("Terneuzen.currentLevel", listOf("Rijkswaterstaat DDAPI 2.0", "Rijkswaterstaat WFS", "Waterinfo Vlaanderen (availability and datum verification pending)")),
    SourcePriority("Terneuzen.highLow", listOf("Rijkswaterstaat DDAPI 2.0", "Waterinfo Vlaanderen verified tide prediction", "Rijkswaterstaat astronomical tide (explicitly labeled)")),
    SourcePriority("Yerseke.currentLevel", listOf("Rijkswaterstaat DDAPI 2.0", "Rijkswaterstaat WFS")),
    SourcePriority("Yerseke.highLow", listOf("Rijkswaterstaat DDAPI 2.0", "Rijkswaterstaat astronomical tide (explicitly labeled)", "Waterkaart (license and access verification pending)")),
    SourcePriority("NL.wind", listOf("Open-Meteo (working)", "KNMI EDR (access verification pending)")),
    SourcePriority("NL.visibility", listOf("Open-Meteo (working)", "KNMI EDR (access verification pending)")),
    SourcePriority("Nijmegen.officialMGD", listOf("Rijkswaterstaat Vaarweginformatie (official publication only)")),
    SourcePriority("Nijmegen.supplementalDepth", listOf("RhinePlanner (orientation only, not MGD)"))
)
// This registry expresses desired priorities; a provider is NOT active until its
// HTTP reader, response validation and screen binding have been implemented.
// Fallbacks must not silently mix astronomical and meteorological predictions.

/** Returns the first valid value, keeping the source label for UI attribution.
 * An exception, timeout, empty response or rejected value triggers the next reader.
 * Never silently replace an official measurement with a different metric.
 */
internal suspend fun <T : Any> firstValidSource(
    readers: List<Pair<String, suspend () -> T?>>,
    valid: (T) -> Boolean
): Pair<String, T>? {
    for ((name, reader) in readers) {
        val result = try { reader() } catch (_: Exception) { null }
        if (result != null && valid(result)) return name to result
    }
    return null
}
