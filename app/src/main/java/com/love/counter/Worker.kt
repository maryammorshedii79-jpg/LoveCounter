package com.love.counter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

data class Ev(val id: Long, val name: String, val emoji: String, val dt: LocalDateTime, val notify: Boolean)

val DEFAULT_START: LocalDateTime = LocalDateTime.of(2022, 12, 11, 23, 54)

fun loadStart(sp: SharedPreferences): LocalDateTime =
    try { LocalDateTime.parse(sp.getString("start", null)!!) } catch (e: Exception) { DEFAULT_START }

fun eventsToJson(l: List<Ev>): String {
    val a = JSONArray()
    l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji).put("dt", it.dt.toString()).put("n", it.notify)) }
    return a.toString()
}

fun loadEvents(sp: SharedPreferences): List<Ev> {
    val s = sp.getString("ev", null)
    if (s != null) {
        try {
            val a = JSONArray(s)
            return (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Ev(o.getLong("id"), o.getString("name"), o.getString("emoji"), LocalDateTime.parse(o.getString("dt")), o.getBoolean("n"))
            }
        } catch (e: Exception) { }
    }
    return listOf(
        Ev(1, "Maryam", "🎂", LocalDateTime.of(2000, 12, 28, 5, 30), true),
        Ev(2, "Omid", "🎂", LocalDateTime.of(2000, 8, 5, 19, 10), true)
    )
}

fun scheduleNotifs(c: Context, on: Boolean) {
    val wm = WorkManager.getInstance(c)
    if (!on) { wm.cancelUniqueWork("daily"); return }
    val now = LocalDateTime.now()
    var t = now.toLocalDate().atTime(9, 0)
    if (t.isBefore(now)) t = t.plusDays(1)
    val delay = Duration.between(now, t).toMinutes()
    wm.enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).setInitialDelay(delay, TimeUnit.MINUTES).build())
}

class DailyWorker(val c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val sp = c.getSharedPreferences("p", 0)
        val fa = sp.getBoolean("fa", true)
        val today = LocalDate.now()
        val start = loadStart(sp).toLocalDate()
        val msgs = mutableListOf<String>()
        val days = ChronoUnit.DAYS.between(start, today)
        if (today.monthValue == start.monthValue && today.dayOfMonth == start.dayOfMonth && today.year > start.year)
            msgs += if (fa) "🎉 سالگرد عشق شما! ${today.year - start.year} سال با هم" else "🎉 Happy anniversary! ${today.year - start.year} years together"
        else if (today.dayOfMonth == start.dayOfMonth && days > 0)
            msgs += if (fa) "💞 امروز ماهگرد شماست" else "💞 Happy monthsary!"
        if (days > 0 && days % 100 == 0L)
            msgs += if (fa) "✨ $days روز از شروع عشقتون گذشته" else "✨ $days days since it all began"
        loadEvents(sp).filter { it.notify }.forEach { e ->
            val b = e.dt.toLocalDate()
            var nx = b.withYear(today.year); if (nx.isBefore(today)) nx = nx.plusYears(1)
            when (ChronoUnit.DAYS.between(today, nx)) {
                0L -> msgs += if (fa) "${e.emoji} امروز: ${e.name}" else "${e.emoji} Today: ${e.name}"
                1L -> msgs += if (fa) "${e.emoji} فردا: ${e.name}" else "${e.emoji} Tomorrow: ${e.name}"
            }
        }
        if (msgs.isEmpty()) return Result.success()
        val nm = NotificationManagerCompat.from(c)
        nm.createNotificationChannel(NotificationChannel("love", "Love", NotificationManager.IMPORTANCE_HIGH))
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        msgs.forEachIndexed { i, m ->
            nm.notify(i, NotificationCompat.Builder(c, "love").setSmallIcon(android.R.drawable.btn_star_big_on)
                .setContentTitle(if (fa) "شمارنده عشق ❤" else "Love Counter ❤").setContentText(m)
                .setContentIntent(pi).setAutoCancel(true).build())
        }
        return Result.success()
    }
}
