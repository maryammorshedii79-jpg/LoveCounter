package com.love.counter

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); enableEdgeToEdge(); setContent { App() } }
}

// ---------- helpers ----------
fun faDigits(s: String, fa: Boolean) =
    if (fa) s.map { if (it in '0'..'9') '۰' + (it - '0') else if (it == ',') '٬' else it }.joinToString("") else s
fun num(n: Long, fa: Boolean) = faDigits(n.toString(), fa)
fun numf(n: Long, fa: Boolean) = faDigits(String.format(Locale.US, "%,d", n), fa)
fun two(n: Long, fa: Boolean) = faDigits(if (n < 10) "0$n" else "$n", fa)

val FA_U = listOf("سال", "ماه", "هفته", "روز", "ساعت", "دقیقه", "ثانیه")
val EN_U = listOf("year", "month", "week", "day", "hour", "minute", "second")
val UNITS = listOf(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.WEEKS, ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)
fun unitLabel(i: Int, v: Long, fa: Boolean) = if (fa) FA_U[i] else EN_U[i] + if (v == 1L) "" else "s"

val FA_M = listOf("ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
fun ord(d: Int) = d.toString() + if (d in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
fun timeText(dt: LocalDateTime, fa: Boolean) = faDigits(String.format(Locale.US, "%02d:%02d", dt.hour, dt.minute), fa)
fun dateText(dt: LocalDateTime, fa: Boolean) =
    if (fa) "${num(dt.dayOfMonth.toLong(), fa)} ${FA_M[dt.monthValue - 1]} ${num(dt.year.toLong(), fa)}"
    else dt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))

/** years, months, days, hours, minutes, seconds between two moments (order doesn't matter) */
fun breakdown(a: LocalDateTime, b: LocalDateTime): LongArray {
    val from = if (a.isAfter(b)) b else a
    val to = if (a.isAfter(b)) a else b
    var c = from
    val out = LongArray(7)
    for (i in 0..6) { val v = UNITS[i].between(c, to); out[i] = v; c = c.plus(v, UNITS[i]) }
    return out
}

fun insight(e: Ev, now: LocalDateTime, fa: Boolean): String? {
    val days = Duration.between(e.dt, now).seconds / 86400.0
    if (days < 1) return null
    var k = (e.id % 3).toInt()
    if (k == 2 && (days / 27.32).toLong() < 1) k = 0
    return when (k) {
        1 -> { val n = numf((days * 2_573_000).toLong(), fa)
            if (fa) "از آن زمان زمین حدود $n کیلومتر دور خورشید حرکت کرده است 🌍" else "Since then, Earth has travelled about $n km around the Sun 🌍" }
        2 -> { val n = numf((days / 27.32).toLong(), fa)
            if (fa) "از آن زمان ماه حدود $n بار دور زمین چرخیده است 🌙" else "Since then, the Moon has circled Earth about $n times 🌙" }
        else -> { val n = numf((days * 100_000).toLong(), fa)
            if (fa) "از آن زمان قلب تو حدود $n بار تپیده است ❤️" else "Since then, your heart has beaten about $n times ❤️" }
    }
}

fun pickDT(c: Context, cur: LocalDateTime, onDone: (LocalDateTime) -> Unit) {
    DatePickerDialog(c, { _, y, m, d ->
        TimePickerDialog(c, { _, h, mi -> onDone(LocalDateTime.of(y, m + 1, d, h, mi)) }, cur.hour, cur.minute, true).show()
    }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
}

fun Modifier.dark(r: Dp = 16.dp) = this
    .background(Color.Black.copy(.38f), RoundedCornerShape(r))
    .border(1.5.dp, Color.White.copy(.5f), RoundedCornerShape(r))

val ACCENT = Color(0xFFB5683A)
val SWATCHES = listOf(0xFF1B0F2E, 0xFF5A1E5E, 0xFFB0305C, 0xFF0F2A44, 0xFF14532D, 0xFF7C2D12, 0xFF2B2B2B, 0xFF3B3B98)

// ---------- animated minimal heart ----------
@Composable
fun BeatingHeart(size: Dp, animate: Boolean) {
    val s = if (animate) {
        val a by rememberInfiniteTransition(label = "h").animateFloat(1f, 1f,
            infiniteRepeatable(keyframes {
                durationMillis = 1300
                1f at 0; 1.28f at 160; 1.06f at 320; 1.2f at 480; 1f at 760
            }), label = "s")
        a
    } else 1f
    Canvas(Modifier.size(size).scale(s)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(w / 2, h * 0.92f)
            cubicTo(-w * 0.18f, h * 0.45f, w * 0.12f, -h * 0.05f, w / 2, h * 0.28f)
            cubicTo(w * 0.88f, -h * 0.05f, w * 1.18f, h * 0.45f, w / 2, h * 0.92f)
            close()
        }
        drawPath(p, Color.White)
    }
}

// ---------- app ----------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App() {
    val c = LocalContext.current
    val sp = remember { c.getSharedPreferences("p", 0) }
    var fa by remember { mutableStateOf(sp.getBoolean("fa", true)) }
    var blur by remember { mutableFloatStateOf(sp.getFloat("blur", 14f)) }
    var dim by remember { mutableFloatStateOf(sp.getFloat("dim", .35f)) }
    var bg by remember { mutableStateOf(sp.getString("bg", null)) }
    var mode by remember { mutableStateOf(sp.getString("mode", if (bg != null) "photo" else "classic")!!) }
    var color by remember { mutableIntStateOf(sp.getInt("color", 0xFF1B0F2E.toInt())) }
    var anim by remember { mutableStateOf(sp.getBoolean("anim", true)) }
    var ins by remember { mutableStateOf(sp.getBoolean("ins", true)) }
    var start by remember { mutableStateOf(loadStart(sp)) }
    val events = remember { mutableStateListOf<Ev>().also { it.addAll(loadEvents(sp)) } }
    var notif by remember { mutableStateOf(sp.getBoolean("n", false)) }
    var screen by remember { mutableIntStateOf(0) }
    var totals by remember { mutableStateOf(setOf<Int>()) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Ev?>(null) }
    var deleting by remember { mutableStateOf<Ev?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    LaunchedEffect(bg) {
        img = bg?.let { u -> withContext(Dispatchers.IO) { runCatching {
            c.contentResolver.openInputStream(Uri.parse(u))?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() } }.getOrNull() } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            runCatching { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            bg = u.toString(); mode = "photo"
            sp.edit().putString("bg", bg).putString("mode", mode).apply()
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun t(f: String, e: String) = if (fa) f else e
    fun saveEvents() = sp.edit().putString("ev", eventsToJson(events)).apply()

    BackHandler(enabled = screen != 0) { screen = 0 }

    val b = breakdown(start, now)
    val tot = LongArray(7) { UNITS[it].between(if (start.isAfter(now)) now else start, if (start.isAfter(now)) start else now) }
    val shadow = TextStyle(shadow = Shadow(Color.Black.copy(.5f), Offset(0f, 2f), 8f))
    val grad = Brush.verticalGradient(listOf(Color(0xFF1B0F2E), Color(0xFF5A1E5E), Color(0xFFB0305C)))

    CompositionLocalProvider(LocalLayoutDirection provides (if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        Box(Modifier.fillMaxSize().then(if (mode == "color") Modifier.background(Color(color)) else Modifier.background(grad))) {
            if (mode == "photo") img?.let { Image(it, null, Modifier.fillMaxSize().blur(blur.dp), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(dim)))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                // header
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    when (screen) {
                        0 -> IconBtn("⚙") { screen = 2 }
                        else -> IconBtn(if (fa) "→" else "←") { screen = 0 }
                    }
                    Text("♥  " + when (screen) { 0 -> t("شمارنده عشق", "Love Counter"); 1 -> t("رویدادها", "Events"); else -> t("تنظیمات", "Settings") } + "  ♥",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    when (screen) {
                        0 -> Box(Modifier.size(44.dp).clickable { screen = 1 }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                Text("♥", color = Color(0xFF222222), fontSize = 16.sp) } }
                        1 -> IconBtn("+", 34.sp) { editing = Ev(0, "", "🎉", LocalDateTime.now().withSecond(0).withNano(0), true) }
                        else -> IconBtn("•••", 20.sp) { menu = true }
                    }
                }

                // ---- main ----
                if (screen == 0) {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Maryam", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light, style = shadow)
                            BeatingHeart(26.dp, anim)
                            Text("Omid", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light, style = shadow)
                        }
                        Text("Everything started on " + start.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " at " + timeText(start, false), color = Color.White.copy(.7f),
                            fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(36.dp))
                        Text("♥   " + t("مدت عاشقی ما", "In love for") + "   ♥", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, style = shadow)
                        Spacer(Modifier.height(16.dp))
                        for (i in 0..6) {
                            val showT = i in totals
                            val v = if (showT) tot[i] else b[i]
                            Text("${if (showT) numf(v, fa) else num(v, fa)} ${unitLabel(i, v, fa)}",
                                color = if (showT) Color(0xFFFF8FB1) else Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                                style = shadow, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp).combinedClickable(
                                    onClick = { totals = if (showT) totals - i else totals + i },
                                    onLongClick = {
                                        val msg = "Maryam ❤ Omid\n" + t("مدت عاشقی ما", "In love for") + ":\n" +
                                            (0..6).joinToString("\n") { "${num(b[it], fa)} ${unitLabel(it, b[it], fa)}" }
                                        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), null))
                                    }))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TipBox(t("نکته", "Tip"), t("روی عدد بزن تا مجموعش را ببینی.\nنگه دار تا به اشتراک بگذاری.", "Tap the value to see its total.\nHold to share."))
                }

                // ---- events ----
                if (screen == 1) {
                    Spacer(Modifier.height(16.dp))
                    SearchBar(query, { query = it }, t("جستجوی رویدادها", "Search for events"))
                    val list = events.filter { query.isBlank() || it.name.contains(query, true) }
                    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                        items(list, key = { it.id }) { e ->
                            EventItem(e, now, fa, ins, expanded == e.id,
                                onToggle = { expanded = if (expanded == e.id) null else e.id },
                                onShare = {
                                    val bd = breakdown(e.dt, now)
                                    val parts = (0..6).filter { bd[it] > 0 }.joinToString(", ") { "${num(bd[it], fa)} ${unitLabel(it, bd[it], fa)}" }
                                    val msg = "${e.emoji} ${e.name}\n${dateText(e.dt, fa)} ${timeText(e.dt, fa)}\n" +
                                        (if (e.dt.isAfter(now)) t("زمان باقی‌مانده", "Time left") else t("گذشته", "It's been")) + ": $parts"
                                    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), null))
                                },
                                onNotify = {
                                    val i = events.indexOfFirst { it.id == e.id }
                                    if (i >= 0) { events[i] = e.copy(notify = !e.notify); saveEvents() }
                                },
                                onEdit = { editing = e },
                                onDelete = { deleting = e })
                        }
                    }
                }

                // ---- settings ----
                if (screen == 2) {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("♥   " + t("روزی که همه‌چیز شروع شد", "The day it all began") + "   ♥", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, style = shadow)
                        Spacer(Modifier.height(24.dp))
                        Text(t("شما آشنا شدید در", "You met on"), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, style = shadow, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        val bold = SpanStyle(fontWeight = FontWeight.Bold)
                        Text(buildAnnotatedString {
                            if (fa) {
                                withStyle(bold) { append("${num(start.dayOfMonth.toLong(), true)} ${FA_M[start.monthValue - 1]} ${num(start.year.toLong(), true)}") }
                                append("\nساعت "); withStyle(bold) { append(timeText(start, true)) }
                            } else {
                                append("The "); withStyle(bold) { append(ord(start.dayOfMonth)) }
                                append(" of "); withStyle(bold) { append(start.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)) }
                                append(", "); withStyle(bold) { append(start.year.toString()) }
                                append("\nat "); withStyle(bold) { append(timeText(start, false)) }
                            }
                        }, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, lineHeight = 44.sp, style = shadow)
                        Spacer(Modifier.height(26.dp))
                        Box(Modifier.dark(18.dp).clickable {
                            pickDT(c, start) { start = it; sp.edit().putString("start", it.toString()).apply() }
                        }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                            Text(t("ویرایش تاریخ", "Edit date"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    Spacer(Modifier.weight(1f))
                    TipBox(t("از «تنظیمات» لذت می‌بری؟", "Enjoying Love Counter?"),
                        t("منوی ••• را باز کن تا گزینه‌های بیشتر را ببینی!", "Open the ••• menu to unlock the full experience!"))
                }
            }

            if (menu) SettingsSheet(
                fa = fa, notif = notif, ins = ins, mode = mode, blur = blur, dim = dim, color = color, anim = anim, hasPhoto = bg != null,
                onNotif = { notif = it; sp.edit().putBoolean("n", it).apply(); scheduleNotifs(c, it)
                    if (it) perm.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onIns = { ins = it; sp.edit().putBoolean("ins", it).apply() },
                onMode = { mode = it; sp.edit().putString("mode", it).apply() },
                onBlur = { blur = it; sp.edit().putFloat("blur", it).apply() },
                onDim = { dim = it; sp.edit().putFloat("dim", it).apply() },
                onColor = { color = it; mode = "color"; sp.edit().putInt("color", it).putString("mode", "color").apply() },
                onAnim = { anim = it; sp.edit().putBoolean("anim", it).apply() },
                onPhoto = { picker.launch(arrayOf("image/*")) },
                onRemovePhoto = { bg = null; img = null; mode = "classic"; sp.edit().remove("bg").putString("mode", "classic").apply() },
                onLang = { fa = it; sp.edit().putBoolean("fa", it).apply() },
                onDismiss = { menu = false })

            editing?.let { e ->
                EventDialog(e, fa, onCancel = { editing = null }, onSave = { n ->
                    if (n.id == 0L) events.add(n.copy(id = System.currentTimeMillis()))
                    else { val i = events.indexOfFirst { it.id == n.id }; if (i >= 0) events[i] = n }
                    saveEvents(); editing = null
                })
            }
            deleting?.let { e ->
                AlertDialog(onDismissRequest = { deleting = null },
                    text = { Text(t("«${e.name}» حذف شود؟", "Delete \"${e.name}\"?")) },
                    confirmButton = { TextButton({ events.removeAll { it.id == e.id }; saveEvents(); deleting = null }) { Text(t("حذف", "Delete")) } },
                    dismissButton = { TextButton({ deleting = null }) { Text(t("انصراف", "Cancel")) } })
            }
        }
    }
}

@Composable
fun IconBtn(txt: String, size: TextUnit = 28.sp, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(txt, color = Color.White, fontSize = size, fontWeight = FontWeight.Bold) }

@Composable
fun TipBox(title: String, body: String) =
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ⓘ   $title   ⓘ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Color.White.copy(.85f), fontSize = 15.sp, fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }

@Composable
fun SearchBar(v: String, onChange: (String) -> Unit, hint: String) {
    Row(Modifier.fillMaxWidth().height(52.dp).dark(26.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔍", fontSize = 18.sp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (v.isEmpty()) Text(hint, color = Color.White.copy(.6f), fontSize = 18.sp)
            BasicTextField(v, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color.White))
        }
    }
}

@Composable
fun ActBtn(text: String, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.dark(16.dp).clickable(onClick = onClick).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventItem(e: Ev, now: LocalDateTime, fa: Boolean, showInsight: Boolean, open: Boolean,
              onToggle: () -> Unit, onShare: () -> Unit, onNotify: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    fun t(f: String, en: String) = if (fa) f else en
    val future = e.dt.isAfter(now)
    val bd = breakdown(e.dt, now)
    val parts = (0..6).filter { bd[it] > 0 }.map { "${num(bd[it], fa)} ${unitLabel(it, bd[it], fa)}" }
        .ifEmpty { listOf("${num(0, fa)} ${unitLabel(6, 0, fa)}") }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(44.dp).fillMaxHeight()) {
            Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().background(Color.White.copy(.45f)))
            val sz = if (open) 32.dp else 24.dp
            Box(Modifier.align(Alignment.TopCenter).padding(top = 34.dp - sz / 2).size(sz).clip(CircleShape)
                .background(Color(0xFF140A1F)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (open) 14.dp else 6.dp).clip(CircleShape).background(Color.White)) }
        }
        Column(Modifier.weight(1f).padding(top = 18.dp, bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Text("  " + e.emoji, fontSize = 24.sp)
                    }
                    Text(t("در ", "on ") + dateText(e.dt, fa) + t("\nساعت ", "\nat ") + timeText(e.dt, fa) + " — " + (if (e.notify) "🔔" else "🔕"),
                        color = Color.White.copy(.8f), fontSize = 19.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 6.dp))
                }
                Text(if (open) "▴" else "▾", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
            if (open) {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(.8f)))
                Spacer(Modifier.height(18.dp))
                Text("🕒  " + (if (future) t("زمان باقی‌مانده:", "Time left:") else t("گذشته است:", "It's been:")),
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    parts.forEach { p ->
                        Text(p, color = Color.White, fontSize = 18.sp,
                            modifier = Modifier.background(Color.White.copy(.12f), RoundedCornerShape(50))
                                .border(1.5.dp, Color.White.copy(.5f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                val tx = if (showInsight && !future) insight(e, now, fa) else null
                if (tx != null) {
                    Spacer(Modifier.height(20.dp))
                    Text("✦  " + t("نکته:", "Insight:"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(tx, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActBtn("↗  " + t("اشتراک", "Share"), Modifier.weight(1f), onShare)
                    ActBtn(if (e.notify) "🔕  " + t("اعلان نده", "Don't notify") else "🔔  " + t("اعلان بده", "Notify"), Modifier.weight(1f), onNotify)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActBtn("✎  " + t("ویرایش", "Edit"), Modifier.weight(1f), onEdit)
                    ActBtn("🗑  " + t("حذف", "Delete"), Modifier.weight(1f), onDelete)
                }
            }
        }
    }
}

@Composable
fun EventDialog(e: Ev, fa: Boolean, onSave: (Ev) -> Unit, onCancel: () -> Unit) {
    val c = LocalContext.current
    fun t(f: String, en: String) = if (fa) f else en
    var name by remember { mutableStateOf(e.name) }
    var emoji by remember { mutableStateOf(e.emoji) }
    var dt by remember { mutableStateOf(e.dt) }
    var nt by remember { mutableStateOf(e.notify) }
    val tf = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFF8FB1), unfocusedBorderColor = Color.White.copy(.5f),
        focusedLabelColor = Color(0xFFFF8FB1), unfocusedLabelColor = Color.White.copy(.7f), cursorColor = Color.White)
    CompositionLocalProvider(LocalLayoutDirection provides (if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        Dialog(onDismissRequest = onCancel) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF241338)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (e.id == 0L) t("رویداد جدید", "New event") else t("ویرایش رویداد", "Edit event"),
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it }, label = { Text(t("عنوان", "Title")) }, singleLine = true, colors = tf, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emoji, { emoji = it }, label = { Text(t("ایموجی", "Emoji")) }, singleLine = true, colors = tf, modifier = Modifier.fillMaxWidth())
                ActBtn("${dateText(dt, fa)} — ${timeText(dt, fa)}", Modifier.fillMaxWidth()) { pickDT(c, dt) { dt = it } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("اعلان", "Notify"), color = Color.White, modifier = Modifier.weight(1f))
                    Switch(nt, { nt = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActBtn(t("انصراف", "Cancel"), Modifier.weight(1f), onCancel)
                    ActBtn(t("ذخیره", "Save"), Modifier.weight(1f)) {
                        if (name.isNotBlank()) onSave(e.copy(name = name.trim(), emoji = emoji.ifBlank { "🎉" }, dt = dt, notify = nt))
                    }
                }
            }
        }
    }
}

// ---------- settings bottom sheet ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(fa: Boolean, notif: Boolean, ins: Boolean, mode: String, blur: Float, dim: Float, color: Int, anim: Boolean, hasPhoto: Boolean,
                  onNotif: (Boolean) -> Unit, onIns: (Boolean) -> Unit, onMode: (String) -> Unit, onBlur: (Float) -> Unit, onDim: (Float) -> Unit,
                  onColor: (Int) -> Unit, onAnim: (Boolean) -> Unit, onPhoto: () -> Unit, onRemovePhoto: () -> Unit,
                  onLang: (Boolean) -> Unit, onDismiss: () -> Unit) {
    fun t(f: String, e: String) = if (fa) f else e
    var showAdj by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    val ink = Color(0xFF14161B); val sub = Color(0xFF6B7280); val field = Color(0xFFF1F3F8); val line = Color(0xFFE3E6EE)
    val sw = SwitchDefaults.colors(checkedTrackColor = ACCENT, checkedThumbColor = Color.White, checkedBorderColor = ACCENT)
    val sl = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT)
    CompositionLocalProvider(LocalLayoutDirection provides (if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                @Composable fun Sep() = Box(Modifier.fillMaxWidth().height(2.dp).background(line))
                @Composable fun Head(icon: String, title: String, desc: String) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(icon, fontSize = 20.sp, color = ink); Spacer(Modifier.width(10.dp))
                        Text(title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    Text(desc, color = sub, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
                }
                // notifications
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Head("🔔", t("اعلان‌ها", "Notifications"), t("یادآوری سالگردها و رویدادها.", "Get reminders for anniversaries and events.")) }
                    Switch(notif, onNotif, colors = sw)
                }
                Sep()
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Head("✦", t("نکته‌های جالب", "Insights"), t("حقایق جالب بر اساس زمان شما و رویدادها.", "See fun facts based on your time together and events.")) }
                    Switch(ins, onIns, colors = sw)
                }
                Sep()
                // appearance
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Head("🎨", t("ظاهر", "Appearance"), t("سبک پس‌زمینه را انتخاب کن و ظاهر برنامه را شخصی‌سازی کن.", "Choose a background style and customize the look of the app."))
                    Row(Modifier.fillMaxWidth().background(field, RoundedCornerShape(18.dp)).padding(4.dp)) {
                        listOf("classic" to ("◐  " + t("کلاسیک", "Classic")), "photo" to ("📷  " + t("عکس", "Photo")), "color" to ("🎨  " + t("رنگ", "Color"))).forEach { (m, label) ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (mode == m) ACCENT else Color.Transparent)
                                .clickable { onMode(m) }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                                Text(label, color = if (mode == m) Color.White else ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(52.dp).background(field, RoundedCornerShape(16.dp)).border(1.5.dp, line, RoundedCornerShape(16.dp))
                            .clickable(enabled = mode == "photo") { onPhoto() }, contentAlignment = Alignment.Center) {
                            Text(when (mode) { "photo" -> "🖼  " + (if (hasPhoto) t("تعویض عکس", "Replace photo") else t("انتخاب عکس", "Choose photo"))
                                "color" -> t("رنگ دلخواه", "Custom color"); else -> t("گرادیان پیش‌فرض", "Default gradient") },
                                color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                        Box(Modifier.size(52.dp).background(if (showAdj) ACCENT else field, RoundedCornerShape(16.dp)).border(1.5.dp, line, RoundedCornerShape(16.dp))
                            .clickable { showAdj = !showAdj }, contentAlignment = Alignment.Center) { Text("💧", fontSize = 20.sp) }
                        Box(Modifier.size(52.dp).background(if (anim) ACCENT else field, RoundedCornerShape(16.dp)).border(1.5.dp, line, RoundedCornerShape(16.dp))
                            .clickable { onAnim(!anim) }, contentAlignment = Alignment.Center) {
                            Text("♥", fontSize = 22.sp, color = if (anim) Color.White else ink) }
                    }
                    if (mode == "photo" && hasPhoto) Text(t("حذف عکس", "Remove photo"), color = ACCENT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onRemovePhoto).padding(vertical = 2.dp))
                    if (mode == "color") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            SWATCHES.forEach { s ->
                                val sel = Color(s).toArgb() == color
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(s)).border(if (sel) 3.dp else 1.dp, if (sel) ACCENT else line, CircleShape)
                                    .clickable { onColor(Color(s).toArgb()) })
                            }
                        }
                        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
                        Text(t("طیف رنگ", "Hue"), color = sub, fontSize = 15.sp)
                        Slider(hsv[0], { onColor(android.graphics.Color.HSVToColor(floatArrayOf(it, .6f, .42f))) }, valueRange = 0f..360f, colors = sl)
                    }
                    if (showAdj) {
                        if (mode == "photo") {
                            Text(t("میزان بلور: ", "Blur: ") + num(blur.toLong(), fa), color = sub, fontSize = 15.sp)
                            Slider(blur, onBlur, valueRange = 0f..25f, colors = sl)
                        }
                        Text(t("تیرگی پس‌زمینه: ", "Darkness: ") + num((dim * 100).toLong(), fa) + "%", color = sub, fontSize = 15.sp)
                        Slider(dim, onDim, valueRange = 0f..0.8f, colors = sl)
                    }
                }
                Sep()
                // language
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Head("🌐", t("زبان", "Language"), t("زبان دلخواه برنامه را انتخاب کن.", "Choose the language you'd like Love Counter to speak."))
                    Box {
                        Row(Modifier.fillMaxWidth().height(56.dp).background(field, RoundedCornerShape(16.dp)).border(1.5.dp, line, RoundedCornerShape(16.dp))
                            .clickable { langMenu = true }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (fa) "فارسی" else "English", color = ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("▾", color = ink, fontSize = 18.sp)
                        }
                        DropdownMenu(langMenu, { langMenu = false }) {
                            DropdownMenuItem(text = { Text("فارسی") }, onClick = { onLang(true); langMenu = false })
                            DropdownMenuItem(text = { Text("English") }, onClick = { onLang(false); langMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
