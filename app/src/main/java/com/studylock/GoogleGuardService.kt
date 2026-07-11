package com.studylock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 전경 액티비티 감시.
 * 구글 앱(googlequicksearchbox)이 '검색' 등 어시스턴트가 아닌 화면으로 뜨면
 * (= 설정→열기·알림 탭 같은 탈출 경로) 즉시 키오스크를 앞으로 끌어 되튕긴다.
 * Gemini(어시스턴트 surface — 클래스에 assistant/robin/bard/gemini/opa 포함)는 통과시킨다.
 *
 * 프로세스를 kill 하지 않는 이유: Gemini 와 구글검색이 같은 googlequicksearchbox 프로세스라,
 * 죽이면 Gemini 도 깨진다(재시작 전까지 안 열림). 그래서 열품타처럼 '되튕기기'만 한다.
 * lockTask 화이트리스트엔 구글 앱이 남아 있어야 Gemini 가 열리므로(패키지 단위),
 * 액티비티 단위 판정은 이 접근성 감시로만 가능하다.
 */
class GoogleGuardService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != GOOGLE_APP) return

        // 잠금 중이 아니거나(셋업 전/완전 해제), 임시·전체 해제 시간 중이면 감시 안 함(그땐 폰 자유)
        val prefs = Prefs(this)
        if (!prefs.locked || prefs.tempUnlockActive()) return

        val cls = event.className?.toString().orEmpty()
        // 알려진 '구글 검색/구글앱 홈' 화면만 튕긴다. 게이트웨이·어시스턴트(Gemini) 등은 전부 통과.
        if (BLOCK.any { cls.contains(it, ignoreCase = true) }) bounceToKiosk()
    }

    override fun onInterrupt() {}

    /** 스터디락 키오스크를 전면으로 → 구글 검색 화면을 덮어 못 쓰게 한다(프로세스는 살려둠). */
    private fun bounceToKiosk() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    companion object {
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        // 이 클래스명이 걸리면 '구글 검색/구글앱' 탈출로 보고 튕긴다. 그 외(게이트웨이·어시스턴트)는 통과.
        private val BLOCK = listOf("SearchActivity", "GoogleAppActivity")

        /** 이 접근성 서비스가 실제로 켜져 있나 (설정의 enabled 목록에 우리 컴포넌트가 있나) */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val pkg = context.packageName
            val full = "$pkg/$pkg.GoogleGuardService"
            val short = "$pkg/.GoogleGuardService"
            return flat.split(":").any { it.equals(full, true) || it.equals(short, true) }
        }
    }
}
