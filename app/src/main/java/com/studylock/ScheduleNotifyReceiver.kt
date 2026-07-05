package com.studylock

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 일정 경계 알람 수신 → heads-up 알림 표시 → 다음 경계 재예약.
 * 종료/시작/변경 3종. 디자인: 모노크롬 아이콘 + 제목=대상 일정 + BigText.
 */
class ScheduleNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val type = intent.getStringExtra("type")
        val endLabel = intent.getStringExtra("endLabel")
        val startLabel = intent.getStringExtra("startLabel")
        val time = intent.getStringExtra("timeLabel") ?: ""

        if (type != null) {
            ScheduleNotifier.ensureChannel(context)

            // 제목 = '지금 뭘 할지'(글랜서블), 본문 = 맥락
            val title: String
            val text: String
            val big: String
            when (type) {
                "change" -> {
                    title = startLabel ?: "다음 일정"
                    text = "‘${endLabel}’ 끝 · 이제 시작"
                    big = "‘${endLabel}’ 이(가) 끝났어요.\n이제 ‘${startLabel}’ 시작할 시간이에요."
                }
                "end" -> {
                    title = endLabel ?: "일정 종료"
                    text = "끝났어요 · 다음 일정까지 휴식"
                    big = "‘${endLabel}’ 이(가) 끝났어요.\n다음 일정까지 잠깐 쉬어가요."
                }
                else -> {   // start
                    title = startLabel ?: "일정 시작"
                    text = "시작할 시간이에요"
                    big = "이제 ‘${startLabel}’ 시작할 시간이에요."
                }
            }
            val header = when (type) { "change" -> "일정 변경"; "end" -> "일정 종료"; else -> "일정 시작" }

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, ScheduleNotifier.CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(0xFF111111.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(if (time.isNotBlank()) "$header · $time" else header)
                .setStyle(NotificationCompat.BigTextStyle().bigText(big).setSummaryText(header))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            val id = 9700 + ((System.currentTimeMillis() / 1000) % 1000).toInt()
            runCatching { NotificationManagerCompat.from(context).notify(id, n) }
            vibrate(context, prefs.scheduleVibeMode)
        }
        ScheduleNotifier.reschedule(context, prefs)
        // 경계에서 진행 중 일정이 바뀌었으니 라이브 알림 갱신/시작
        runCatching { ScheduleFloatService.start(context) }
    }

    /** 0=끄기 1=짧게 2=길게(3초 동안 3번) */
    private fun vibrate(context: Context, mode: Int) {
        if (mode == 0) return
        val pattern = if (mode == 2) longArrayOf(0, 700, 500, 700, 500, 700)   // ≈3초 동안 3번
                      else longArrayOf(0, 250)                                  // 짧게 1번
        val vib = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        } ?: return
        runCatching { vib.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1)) }
    }
}
