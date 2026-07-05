package com.studylock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews

/**
 * 진행 중인 일정을 '알림 패널 안의 진행 알림'으로 표시한다.
 * (Now Bar/미디어 방식은 삼성 OngoingCard 글리치로 터치 먹통이 나서 폐기)
 * 상단 상태바엔 아이콘만, 알림 패널을 열면 제목·진행바·다음 일정·D-day 가 보인다.
 */
class ScheduleFloatService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // startForegroundService 계약: 즉시 startForeground
        runCatching { startForeground(NOTIF_ID, baseNotif()) }
        tick.run()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        update()
        return START_STICKY
    }

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 10_000)   // 진행바·남은시간 갱신(부드럽게)
        }
    }

    private fun update() = runCatching {
        val prefs = Prefs(this)
        if (!prefs.locked || !prefs.floatingSchedule) { stopNow(); return@runCatching }
        val info = SchedLive.of(this, prefs) ?: run { stopNow(); return@runCatching }
        startForeground(NOTIF_ID, buildNotif(info))
    }

    private fun gotoPI(goto: String, req: Int): PendingIntent = PendingIntent.getActivity(
        this, req,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("goto", goto),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotif(info: SchedLive): Notification {
        val open = gotoPI("kiosk", 10)
        val remainMin = ((info.totalSec - info.elapsedSec) / 60).toInt()
        val promille = (info.elapsedSec * 1000 / info.totalSec.coerceAtLeast(1)).toInt().coerceIn(0, 1000)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val primary = if (night) Color.WHITE else Color.parseColor("#111111")
        val secondary = if (night) Color.parseColor("#C4C4C4") else Color.parseColor("#555555")

        // 확장 뷰: 제목·진행바·다음일정·버튼 4개 (꼬끼오알람식)
        val rv = RemoteViews(packageName, R.layout.notif_schedule_big).apply {
            setTextViewText(R.id.notif_title, info.title); setTextColor(R.id.notif_title, primary)
            setTextViewText(R.id.notif_sub, "${remainMin}분 남음 · ${info.subtitle} · ${info.ddayLabel} ${info.ddayNum}")
            setTextColor(R.id.notif_sub, secondary)
            setProgressBar(R.id.notif_progress, 1000, promille, false)
            runCatching {
                setColorStateList(R.id.notif_progress, "setProgressTintList", ColorStateList.valueOf(primary))
                setColorStateList(R.id.notif_progress, "setProgressBackgroundTintList",
                    ColorStateList.valueOf(Color.parseColor("#33808080")))
            }
            setInt(R.id.notif_btn_timetable, "setColorFilter", primary); setOnClickPendingIntent(R.id.notif_btn_timetable, gotoPI("timetable", 11))
            setInt(R.id.notif_btn_focus, "setColorFilter", primary); setOnClickPendingIntent(R.id.notif_btn_focus, gotoPI("focus", 12))
            setInt(R.id.notif_btn_screentime, "setColorFilter", primary); setOnClickPendingIntent(R.id.notif_btn_screentime, gotoPI("screentime", 13))
            setInt(R.id.notif_btn_settings, "setColorFilter", primary); setOnClickPendingIntent(R.id.notif_btn_settings, gotoPI("settings", 14))
        }

        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(info.title)
            .setContentText("${remainMin}분 남음 · ${info.subtitle}")
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun baseNotif(): Notification =
        Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("진행 중인 일정")
            .setOngoing(true)
            .build()

    private fun stopNow() {
        runCatching { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true) }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "진행 중인 일정", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false); setSound(null, null); enableVibration(false) }
            )
        }
    }

    companion object {
        private const val NOTIF_ID = 4802
        private const val CH = "schedlive"

        fun start(ctx: Context) {
            val p = Prefs(ctx)
            if (!p.locked || !p.floatingSchedule) return
            runCatching {
                val i = Intent(ctx, ScheduleFloatService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, ScheduleFloatService::class.java)) }
        }

        /** 화면 전환/경계 등에서 갱신 트리거 */
        fun setForeground(ctx: Context, foreground: Boolean) {
            start(ctx)
        }
    }
}

/** 진행 알림에 넣을 값 */
data class SchedLive(
    val title: String,
    val subtitle: String,
    val totalSec: Long,
    val elapsedSec: Long,
    val ddayLabel: String,
    val ddayNum: String,
) {
    companion object {
        fun of(ctx: Context, prefs: Prefs): SchedLive? {
            val blocks = runCatching {
                TimetableLoader.parse(TimetableLoader.activeJson(ctx, prefs)).getOrNull()?.blocks
            }.getOrNull()?.sortedBy { it.startMin } ?: return null
            if (blocks.isEmpty()) return null

            val t = java.time.LocalTime.now()
            val nowMin = t.hour * 60 + t.minute
            val idx = TimetableLoader.currentIndex(blocks, nowMin)
            if (idx < 0) return null
            val b = blocks[idx]
            val nowAdj = if (nowMin < b.startMin) nowMin + 1440 else nowMin
            val totalSec = ((b.endMin - b.startMin).coerceAtLeast(1)) * 60L
            val elapsedSec = (((nowAdj - b.startMin) * 60L) + t.second).coerceIn(0, totalSec)
            val nb = blocks.getOrNull(idx + 1)
            val subtitle = if (nb != null) "다음 ${nb.content} ${nb.start}" else "${b.start}–${b.end}"
            val days = DateUtil.daysUntil(prefs.targetDateMillis)
            val ddayNum = if (days >= 0) "D-$days" else "D+${-days}"
            return SchedLive(b.content, subtitle, totalSec, elapsedSec, prefs.ddayLabel, ddayNum)
        }
    }
}
