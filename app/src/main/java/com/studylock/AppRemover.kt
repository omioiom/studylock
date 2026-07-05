package com.studylock

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * 사용자 설치 앱 정리. Device Owner 권한일 때 PackageInstaller 로 무확인(silent) 삭제한다.
 * 셋업 단계에서 "불필요한 앱"을 골라 한 번에 제거하는 용도.
 */
object AppRemover {

    private const val ACTION_RESULT = "com.studylock.UNINSTALL_RESULT"

    /**
     * 삭제 후보 = 런처에 보이는 "사용자 설치" 앱(시스템 앱 제외, 자기 자신 제외).
     * 시스템 앱은 완전 삭제가 불가하므로 목록에서 뺀다.
     */
    fun uninstallableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg -> runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() }
            .filter { ai ->
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            }
            .mapNotNull { ai ->
                runCatching {
                    AppEntry(
                        packageName = ai.packageName,
                        label = pm.getApplicationLabel(ai).toString(),
                        icon = pm.getApplicationIcon(ai)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Device Owner 권한으로 무확인 삭제. 결과는 무시(fire-and-forget). */
    fun uninstall(context: Context, packageName: String) {
        val pi = context.packageManager.packageInstaller
        val intent = Intent(ACTION_RESULT).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        runCatching { pi.uninstall(packageName, pending.intentSender) }
    }

    fun uninstallAll(context: Context, packages: Collection<String>) {
        packages.forEach { uninstall(context, it) }
    }
}
