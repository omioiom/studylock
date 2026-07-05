package com.studylock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 1분 주기 틱. 진행 중 세션 사용량을 누적하고 스크린타임 차단을 재적용한다.
 * 규칙이 남아있으면 다음 틱을 다시 예약(자기 재예약). 백그라운드에서도 동작.
 */
class ScreenTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val lock = LockManager(context)
        if (!(prefs.locked && lock.isDeviceOwner())) return

        val now = System.currentTimeMillis()
        val t = java.time.LocalTime.now()
        val nowMin = t.hour * 60 + t.minute

        ScreenTime.accrue(prefs, now, clear = false)   // 진행 세션 부분 누적
        lock.applyScreenTime(prefs, now, nowMin)

        // 사용 중인 앱이 방금 차단됐으면 → 홈으로 튕김 (임시해제 중엔 튕기지 않음)
        val sessionPkg = prefs.stSessionPkg
        if (!prefs.tempUnlockActive() && sessionPkg != null &&
            sessionPkg in ScreenTime.blockedApps(prefs, now, nowMin)) {
            ScreenTime.accrue(prefs, now, clear = true)   // 세션 종료
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }
        }

        if (ScreenTime.hasRules(prefs)) schedule(context, now + 60_000L)
    }

    companion object {
        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 8, Intent(context, ScreenTimeReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun schedule(context: Context, at: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            }
        }

        /** 규칙이 있으면 틱 보장, 없으면 취소 */
        fun ensure(context: Context, prefs: Prefs) {
            if (ScreenTime.hasRules(prefs)) schedule(context, System.currentTimeMillis() + 60_000L)
            else cancel(context)
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            runCatching { am.cancel(pendingIntent(context)) }
        }
    }
}
