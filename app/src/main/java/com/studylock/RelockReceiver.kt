package com.studylock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 임시(3분) 해제 종료 알람 수신 → device-owner 정책을 즉시 재적용해 재잠금하고,
 * StudyLock 을 전면으로 끌어와 lockTask 에 재진입시킨다.
 * 백그라운드 액티비티 시작이 막혀도, 정책(홈 별칭/persistent HOME/lockTask 화이트리스트)이
 * 복원되므로 홈 버튼을 누르면 곧바로 키오스크로 돌아온다.
 */
class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val lock = LockManager(context)
        prefs.tempUnlockUntil = 0L

        if (prefs.locked && lock.isDeviceOwner() && !DateUtil.reached(prefs.targetDateMillis)) {
            runCatching { lock.applyPolicies(prefs) }   // 정책 재적용 = 즉시 재잠금
        }

        // 전면화 시도 (lockTask 재진입은 MainActivity.onResume 에서 처리)
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            )
        }
    }

    companion object {
        const val ACTION_RELOCK = "com.studylock.ACTION_RELOCK"
    }
}
