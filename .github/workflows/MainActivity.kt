package com.example.kansolauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KansoLauncherApp() }
    }
}

data class AppInfo(val label: String, val packageName: String)

@Composable
fun KansoLauncherApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kanso_prefs", Context.MODE_PRIVATE) }
    var isNightMode by remember { mutableStateOf(prefs.getBoolean("night_mode", false)) }
    var isAppDrawerOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pinnedPackages by remember { mutableStateOf(prefs.getStringSet("pinned_apps", emptySet()) ?: emptySet()) }

    fun togglePinApp(pkg: String) {
        val updated = pinnedPackages.toMutableSet()
        if (updated.contains(pkg)) {
            updated.remove(pkg)
            Toast.makeText(context, "Убрано с главного экрана", Toast.LENGTH_SHORT).show()
        } else {
            if (updated.size >= 7) {
                Toast.makeText(context, "Максимум 7 приложений", Toast.LENGTH_SHORT).show()
                return
            }
            updated.add(pkg)
            Toast.makeText(context, "Добавлено на главный экран", Toast.LENGTH_SHORT).show()
        }
        pinnedPackages = updated
        prefs.edit().putStringSet("pinned_apps", updated).apply()
    }

    BackHandler(enabled = true) {
        if (isAppDrawerOpen) { isAppDrawerOpen = false; searchQuery = "" }
    }

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        allApps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    val homeApps = remember(allApps, pinnedPackages) {
        allApps.filter { pinnedPackages.contains(it.packageName) }.ifEmpty { allApps.take(5) }
    }

    val bgColor by animateColorAsState(if (isNightMode) Color(0xFF12141D) else Color(0xFFF5F2EB), tween(600), label = "bg")
    val textColor by animateColorAsState(if (isNightMode) Color(0xFFE6E4DD) else Color(0xFF232323), tween(600), label = "text")
    val accentColor by animateColorAsState(if (isNightMode) Color(0xFFFF4D6D) else Color(0xFFBC002D), tween(600), label = "accent")

    Box(
        modifier = Modifier.fillMaxSize().background(bgColor).pointerInput(Unit) {
            detectHorizontalDragGestures { _, dragAmount ->
                if (dragAmount < -40) isAppDrawerOpen = true
                if (dragAmount > 40) { isAppDrawerOpen = false; searchQuery = "" }
            }
        }
    ) {
        SakuraFallingBackground(isNightMode)
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
            TopBar(isNightMode, accentColor, textColor) {
                isNightMode = !isNightMode
                prefs.edit().putBoolean("night_mode", isNightMode).apply()
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (!isAppDrawerOpen) {
                HomeScreenContent(homeApps, textColor, textColor.copy(alpha = 0.5f), accentColor,
                    { launchApp(context, it.packageName) }, { togglePinApp(it.packageName) }, { isAppDrawerOpen = true })
            } else {
                AppDrawerContent(allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }, pinnedPackages,
                    searchQuery, { searchQuery = it }, textColor, textColor.copy(alpha = 0.5f), accentColor,
                    { launchApp(context, it.packageName) }, { togglePinApp(it.packageName) },
                    { isAppDrawerOpen = false; searchQuery = "" })
            }
        }
    }
}

@Composable
fun TopBar(isNightMode: Boolean, accentColor: Color, textColor: Color, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) {
        while (true) {
            val status = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { context.registerReceiver(null, it) }
            batteryLevel = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
            delay(30000)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(accentColor).clickable { onToggleTheme() }, contentAlignment = Alignment.Center) {
            Text(if (isNightMode) "夜" else "日", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("電池 $batteryLevel%", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(apps: List<AppInfo>, textColor: Color, secColor: Color, accent: Color, onClick: (AppInfo) -> Unit, onLongClick: (AppInfo) -> Unit, onOpenAll: () -> Unit) {
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var periodKanji by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
            dateText = SimpleDateFormat("M月d日 EEEE", Locale.JAPANESE).format(now.time)
            periodKanji = when (now.get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> "朝 • Утро"; in 12..17 -> "昼 • День"; in 18..22 -> "夕 • Вечер"; else -> "夜 • Ночь"
            }
            delay(1000)
        }
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(periodKanji, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(timeText, color = textColor, fontSize = 56.sp, fontWeight = FontWeight.ExtraLight, fontFamily = FontFamily.SansSerif)
                Text(dateText, color = secColor, fontSize = 15.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                apps.forEach { app ->
                    Text(app.label, color = textColor, fontSize = 21.sp, modifier = Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onClick(app) }, onLongClick = { onLongClick(app) }).padding(vertical = 2.dp))
                }
            }
            Row(Modifier.clickable { onOpenAll() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("すべて表示  →", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        Column(Modifier.fillMaxHeight().padding(start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            listOf("一", "期", "一", "会").forEach { Text(it, color = textColor.copy(alpha = 0.25f), fontSize = 24.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerContent(apps: List<AppInfo>, pinned: Set<String>, query: String, onQuery: (String) -> Unit, textColor: Color, secColor: Color, accent: Color, onClick: (AppInfo) -> Unit, onLongClick: (AppInfo) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().border(1.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("検索 ", color = accent, fontSize = 14.sp)
            BasicTextField(value = query, onValueChange = onQuery, singleLine = true, textStyle = TextStyle(color = textColor, fontSize = 16.sp), cursorBrush = SolidColor(accent), modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (query.isEmpty()) Text("Поиск (долгий тап — в избранное)", color = secColor, fontSize = 14.sp); inner() })
            if (query.isNotEmpty()) Text("✕", color = secColor, modifier = Modifier.clickable { onQuery("") })
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(apps) { app ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onClick(app) }, onLongClick = { onLongClick(app) }).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(app.label, color = textColor, fontSize = 19.sp)
                    if (pinned.contains(app.packageName)) Text("★", color = accent, fontSize = 14.sp)
                }
            }
        }
        Text("← 戻る (Назад)", color = accent, fontSize = 15.sp, modifier = Modifier.clickable { onBack() }.padding(vertical = 12.dp))
    }
}

@Composable
fun SakuraFallingBackground(isNightMode: Boolean) {
    val petalColor = if (isNightMode) Color(0xFFFFB7C5).copy(alpha = 0.15f) else Color(0xFFFFB7C5).copy(alpha = 0.45f)
    val petals = remember { List(18) { PetalState(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 10f + 8f, Random.nextFloat() * 0.002f + 0.001f, Random.nextFloat() * 6.28f) } }
    val time by rememberInfiniteTransition("sakura").animateFloat(0f, 1000f, infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart), "time")
    Canvas(Modifier.fillMaxSize()) {
        petals.forEach { p ->
            val y = ((p.yRatio + time * p.speed) % 1.1f) * size.height
            val x = (p.xRatio * size.width) + sin(time * 0.1f + p.sway) * 35f
            rotate((time * 5f + p.sway * 50f) % 360f, Offset(x, y)) {
                drawPath(Path().apply { moveTo(x, y); quadraticBezierTo(x + p.size, y - p.size, x + p.size * 2, y); quadraticBezierTo(x + p.size, y + p.size, x, y) }, petalColor)
            }
        }
    }
}

private data class PetalState(val xRatio: Float, val yRatio: Float, val size: Float, val speed: Float, val sway: Float)
private fun launchApp(context: Context, pkg: String) { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } }
