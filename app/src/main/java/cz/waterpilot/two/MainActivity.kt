package cz.waterpilot.two

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val Cyan = Color(0xFF16D8EE)
private val Muted = Color(0xFFACB7C0)
private val White = Color.White
private val Places = listOf("Duisburg", "Rotterdam", "Hansweert", "Terneuzen", "Yerseke", "MGD Nijmegen")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WaterPilotScreen() }
    }
}

@Composable
private fun WaterPilotScreen() {
    var page by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var menu by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    var nijmegen by remember { mutableStateOf<NijmegenEstimate?>(null) }
    var duisburg by remember { mutableStateOf<RhineGauge?>(null) }
    var forecast by remember { mutableStateOf<RhineForecast?>(null) }
    var nl by remember { mutableStateOf<NlWater?>(null) }
    LaunchedEffect(page, refreshKey) {
        nl = null
        weather = null
        duisburg = null
        forecast = null
        nijmegen = null
        if (page == 0) {
            duisburg = withContext(Dispatchers.IO) { runCatching { fetchDuisburgGauge() }.getOrNull() }
            forecast = withContext(Dispatchers.IO) { runCatching { fetchDuisburgForecast() }.getOrNull() }
        }
        if (page in 1..4) nl = withContext(Dispatchers.IO) { runCatching { fetchNlWater(page) }.getOrNull() }
        if (page < 5) weather = withContext(Dispatchers.IO) { runCatching { fetchWeather(page) }.getOrNull() }
        if (page == 5) nijmegen = withContext(Dispatchers.IO) { runCatching { fetchNijmegenEstimate() }.getOrNull() }
    }
    val compact = LocalConfiguration.current.screenHeightDp < 740
    val gap = if (compact) 8.dp else 15.dp
    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black, primary = Cyan)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .systemBarsPadding().padding(horizontal = 17.dp, vertical = 8.dp)
                .pointerInput(page) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag < -65) page = (page + 1).coerceAtMost(Places.lastIndex)
                            if (drag > 65) page = (page - 1).coerceAtLeast(0)
                            drag = 0f
                        },
                        onHorizontalDrag = { change, amount -> drag += amount; change.consume() }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("≋", color = Cyan, fontSize = 26.sp)
                Text("WATER PILOT", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("⚙", color = Muted, fontSize = 23.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("❮", color = if (page > 0) White else Muted, fontSize = 26.sp, modifier = Modifier.clickable { page = (page - 1).coerceAtLeast(0) }.padding(8.dp))
                Box {
                    Text(Places[page] + " ⌄", color = White, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.clickable { menu = true }.padding(8.dp))
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color(0xFF171717)) {
                        Places.forEachIndexed { i, place ->
                            DropdownMenuItem(text = { Text(place, color = White) }, onClick = { page = i; menu = false })
                        }
                    }
                }
                Text("❯", color = if (page < Places.lastIndex) White else Muted, fontSize = 26.sp, modifier = Modifier.clickable { page = (page + 1).coerceAtMost(Places.lastIndex) }.padding(8.dp))
            }
            Divider(color = Muted.copy(alpha = .55f))
            if (page == 5) {
                Text("MINIMÁLNÍ PLAVEBNÍ HLOUBKA", color = Muted, fontSize = 15.sp)
                Text("MGD Nijmegen", color = White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text("Oficiální MGD: —", color = White, fontSize = 22.sp)
                Divider(color = Muted.copy(alpha = .55f))
                Text("RhinePlanner – doplňkové údaje", color = Cyan, fontSize = 19.sp)
                TwoValues("Vodní stav", nijmegen?.waterCm ?: "— cm", "Nakládací hloubka", nijmegen?.loadingCm ?: "— cm")
                Text("Nakládací hloubka je orientační výpočet RhinePlanner, nikoli oficiální MGD ani doporučení k nakládce.", color = Muted, fontSize = 13.sp)
                Text("Zdroj: rhineplanner.com/waterstanden/nijmegen", color = Muted, fontSize = 12.sp)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (page == 0) "VODNÍ STAV" else "HLADINA NAP", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(if (page == 0) "Měření: " + (duisburg?.measuredAt?.replace("T", " ")?.take(16) ?: "—") else "Měření: " + (nl?.level?.let { nlTime(it.time) } ?: "—"), color = Muted, fontSize = 12.sp)
                }
                Text(if (page == 0) (duisburg?.centimeters ?: "— cm") else nlHeight(nl?.level), color = White, fontWeight = FontWeight.Bold, fontSize = if (compact) 45.sp else 58.sp)
                Text("Změna hladiny: —", color = Cyan, fontSize = 15.sp)
                Divider(color = Muted.copy(alpha = .55f))
                if (page == 0) {
                    Text("Předpověď hladiny", color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    TwoValues("Zítra", forecast?.tomorrow ?: "— cm", "Pozítří", forecast?.dayAfter ?: "— cm")
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Příliv / Odliv", color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Astronomická předpověď", color = Cyan, fontSize = 12.sp)
                    }
                    TwoValues("Příští příliv", nlTide(nl?.high), "Příští odliv", nlTide(nl?.low))
                    Text("Výšky v m NAP; bez vlivu počasí.", color = Muted, fontSize = 12.sp)
                }
                Divider(color = Muted.copy(alpha = .55f))
                TwoValues("🌡 Teplota", weather?.temperature ?: "— °C", "≋ Vítr", weather?.windBft ?: "— Bft")
                Text("Směr větru: " + (weather?.direction ?: "—"), color = Cyan, fontSize = 13.sp)
                Divider(color = Muted.copy(alpha = .55f))
                TwoValues("◉ Viditelnost", weather?.visibility ?: "— km", "☁ Počasí", weather?.description ?: "—")
                Divider(color = Muted.copy(alpha = .55f))
                Text(if (page == 0) "Vodní stav: PEGELONLINE (Duisburg-Ruhrort). Předpověď: ${forecast?.source ?: "nedostupná"}, 13:00. Počasí: Open-Meteo." else "Rijkswaterstaat: ${nl?.station ?: "data nedostupná"}. Načteno: ${nl?.let { nlTime(it.loaded) } ?: "—"}. Počasí: Open-Meteo.", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Places.forEachIndexed { i, _ ->
                    Text("●", color = if (i == page) Cyan else Color.DarkGray, fontSize = 14.sp, modifier = Modifier.clickable { page = i }.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun TwoValues(leftTitle: String, leftValue: String, rightTitle: String, rightValue: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(leftTitle, color = Muted, fontSize = 14.sp)
            Text(leftValue, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.width(1.dp).height(43.dp).background(Muted.copy(alpha = .6f)))
        Column(Modifier.weight(1f)) {
            Text(rightTitle, color = Muted, fontSize = 14.sp)
            Text(rightValue, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
