package com.kithandkeen.qonverse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kithandkeen.qonverse.data.Store
import com.kithandkeen.qonverse.ui.allPhrases
import com.kithandkeen.qonverse.ui.reviewCounts
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Picks today's phrase: starred phrases first, then any, changing once a day. */
object PhraseOfDay {
    fun pick(store: Store): com.kithandkeen.qonverse.ui.PhraseRef? {
        val all = allPhrases(store.loadAll())
        if (all.isEmpty()) return null
        val stars = store.starred()
        val pool = all.filter { "${it.pack.pack.id}|${it.phrase.id}" in stars }.ifEmpty { all }
        return pool[(store.today() % pool.size).toInt()]
    }
}

private fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Home-screen widget showing the phrase of the day. */
class PhraseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, mgr, ids)

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PhraseWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val ref = runCatching { PhraseOfDay.pick(Store(ctx)) }.getOrNull()
            val v = RemoteViews(ctx.packageName, R.layout.widget_phrase)
            v.setTextViewText(R.id.w_text, ref?.phrase?.text ?: "Add a pack to start")
            v.setTextViewText(R.id.w_rom, ref?.phrase?.romanization ?: "")
            v.setTextViewText(R.id.w_meaning, ref?.phrase?.meaning ?: "Open Qonverse")
            v.setOnClickPendingIntent(R.id.widget_root, openAppIntent(ctx))
            ids.forEach { mgr.updateAppWidget(it, v) }
        }
    }
}

/** Daily practice reminder with the phrase of the day. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        PhraseWidget.refresh(ctx)
        if (!store.reminderOn) return Result.success()
        val ref = PhraseOfDay.pick(store) ?: return Result.success()
        val (due, new) = reviewCounts(store.loadAll(), store)
        val todays = due + minOf(new, 5)
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(NotificationChannel(CHANNEL, "Daily practice", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val title = if (todays > 0) "$todays phrases to review today" else "Your phrase of the day"
        val body = listOf(ref.phrase.text, ref.phrase.romanization, ref.phrase.meaning).filter { it.isNotBlank() }.joinToString(" · ")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(ctx)).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, n) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "daily"
        private const val WORK = "daily-reminder"

        /** Schedules (or reschedules) the daily reminder at the chosen hour. Also keeps the widget fresh. */
        fun schedule(ctx: Context, hour: Int) {
            val now = LocalDateTime.now()
            var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
