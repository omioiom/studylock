package com.studylock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 부팅 완료 시 잠금 상태를 복원한다.
 *
 * 문제였던 시나리오: 3분/예약 임시해제 창에서는 HomeAlias 가 꺼지고 재잠금은 AlarmManager
 * 알람에만 의존하는데, 재부팅하면 그 알람이 사라지고 앱도 자동 실행되지 않아 폰이 계속
 * 풀린 채 방치됐다. 그래서 부팅 시 임시해제를 종료하고 정책을 다시 걸어(fail-closed)
 * 즉시 재잠금한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = Prefs(context)
        val lock = LockManager(context)
        if (!prefs.locked || !lock.isDeviceOwner() || DateUtil.reached(prefs.targetDateMillis)) return

        // 재부팅 = 임시해제 종료 (재부팅으로 잠금을 우회/연장하지 못하게)
        prefs.tempUnlockUntil = 0L
        runCatching { lock.applyPolicies(prefs) }
        if (prefs.focusLockActive()) runCatching { lock.applyFocusLock() }
        runCatching { ScreenTimeReceiver.ensure(context, prefs) }
        runCatching { ScheduleNotifier.reschedule(context, prefs) }

        // 앱을 띄워 lockTask 재진입 (device owner 는 백그라운드 실행 시작 제한 예외)
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
