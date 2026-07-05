package com.studylock

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 시간표 일정 알림 스케줄러.
 * 블록 경계(시작/종료) 중 '지금 이후 가장 가까운 하나'로 정확 알람을 잡는다.
 * 각 경계에서 무엇이 끝나고 무엇이 시작하는지 판정해 3종으로 알린다:
 *  - 시작만:        "일정 시작"
 *  - 종료만(다음X): "일정 종료"
 *  - 종료+시작(연속): "일정 변경" (하나로 합침)
 */
object ScheduleNotifier {

    const val CHANNEL = "schedule3"   // HIGH(heads-up) 채널. 진동은 수동 제어(3단계 설정)
    private const val REQ = 7701

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.deleteNotificationChannel("schedule"); nm.deleteNotificationChannel("schedule2") }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "일정 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "시간표 일정 시작·종료·변경 알림"
                enableVibration(false)   // 채널 진동 off → 수동 Vibrator 로 3단계(끄기/짧게/길게) 제어
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun receiverIntent(context: Context) = Intent(context, ScheduleNotifyReceiver::class.java)

    private fun cancelPi(context: Context) = PendingIntent.getBroadcast(
        context, REQ, receiverIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun hhmm(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)

    private data class Ev(val min: Int, val type: String, val endLabel: String?, val startLabel: String?)

    private fun eventsOf(blocks: List<TimeBlock>, notifyStart: Boolean, notifyEnd: Boolean): List<Ev> {
        val sorted = blocks.sortedBy { it.startMin }
        val boundaries = (sorted.map { it.startMin } + sorted.map { it.endMin % 1440 }).distinct()
        return boundaries.mapNotNull { t ->
            val ending = sorted.firstOrNull { it.endMin % 1440 == t }?.content
            val starting = sorted.firstOrNull { it.startMin == t }?.content
            when {
                ending != null && starting != null ->
                    if (notifyStart || notifyEnd) Ev(t, "change", ending, starting) else null
                ending != null -> if (notifyEnd) Ev(t, "end", ending, null) else null
                starting != null -> if (notifyStart) Ev(t, "start", null, starting) else null
                else -> null
            }
        }
    }

    private fun blocksOf(json: String?): List<TimeBlock> = runCatching {
        json?.let { TimetableLoader.parse(it).getOrNull()?.blocks }
    }.getOrNull().orEmpty()

    /** 다음 경계로 알람 재설정 (알림 옵션 꺼져 있으면 취소만) */
    fun reschedule(context: Context, prefs: Prefs) {
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(cancelPi(context))
        val notifyStart = prefs.ttNotifyStart
        val notifyEnd = prefs.ttNotifyEnd
        if (!notifyStart && !notifyEnd) return

        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // 오늘 남은 경계 우선
        val todayEvents = eventsOf(blocksOf(TimetableLoader.activeJson(context, prefs)), notifyStart, notifyEnd)
        val future = todayEvents.filter { it.min > nowMin }.minByOrNull { it.min }

        val target: Ev
        val tomorrow: Boolean
        if (future != null) {
            target = future; tomorrow = false
        } else {
            // 오늘 끝 → 내일 첫 경계. 요일별이면 '내일 요일' 시간표로 계산(오늘 것으로 잡던 버그 수정).
            val tmrDow = (TimetableLoader.todayDow() % 7) + 1
            val tmrJson = if (prefs.timetablePerDay) TimetableLoader.editTargetJson(context, prefs, tmrDow)
                          else TimetableLoader.activeJson(context, prefs)
            val tmrEvents = eventsOf(blocksOf(tmrJson), notifyStart, notifyEnd)
            target = tmrEvents.minByOrNull { it.min } ?: return
            tomorrow = true
        }

        val fire = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, target.min / 60)
            set(Calendar.MINUTE, target.min % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (tomorrow) add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = receiverIntent(context).apply {
            putExtra("type", target.type)
            putExtra("endLabel", target.endLabel)
            putExtra("startLabel", target.startLabel)
            putExtra("timeLabel", hhmm(target.min))
        }
        val pi = PendingIntent.getBroadcast(
            context, REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.timeInMillis, pi) }
    }
}
