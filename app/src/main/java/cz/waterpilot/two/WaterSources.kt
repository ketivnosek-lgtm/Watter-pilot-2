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
