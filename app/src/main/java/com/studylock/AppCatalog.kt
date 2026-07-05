package com.studylock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

object AppCatalog {

    /** 화이트리스트 후보에서 제외할 시스템 패키지(설정/런처 등 탈출구) */
    private val excluded = setOf(
        "com.android.settings",
        "com.samsung.android.app.settings",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3"
    )

    /** 런처에서 실행 가능한 앱만 (자기 자신·시스템 설정/런처 제외), 이름순 정렬 */
    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName && it !in excluded }
            .mapNotNull { pkg ->
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    AppEntry(
                        packageName = pkg,
                        label = pm.getApplicationLabel(ai).toString(),
                        icon = pm.getApplicationIcon(ai)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** 패키지명으로 실행 인텐트. 표준 인텐트가 없으면 런처 액티비티를 직접 찾아 명시 인텐트 구성 */
    fun launchIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(packageName)?.let { return it }
        // 폴백: 일부 앱(예: Gemini)은 getLaunchIntentForPackage 가 null → 런처 액티비티 직접 조회
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        @Suppress("DEPRECATION")
        val ri = pm.queryIntentActivities(q, 0).firstOrNull() ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
