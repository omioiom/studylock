package com.studylock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.UserManager

/**
 * Device Owner 권한으로 키오스크 정책을 행사한다.
 * Device Owner 등록은 1회 ADB 로 선행:
 *   adb shell dpm set-device-owner com.studylock/.AdminReceiver
 */
class LockManager(context: Context) {

    private val appCtx = context.applicationContext
    private val dpm = appCtx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = AdminReceiver.component(appCtx)

    /** HOME 별칭 컴포넌트 (manifest 의 activity-alias .HomeAlias) */
    private val homeAlias = ComponentName(appCtx, "com.studylock.HomeAlias")

    private fun setHomeAliasEnabled(enabled: Boolean) {
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            appCtx.packageManager.setComponentEnabledSetting(
                homeAlias, state, PackageManager.DONT_KILL_APP
            )
        }
    }

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(appCtx.packageName)

    /** lockTask 허용 패키지 = 우리 앱 + 허용앱 + 전화 + 설정 + 구글 인프라(로그인/권한창) */
    private fun lockTaskList(prefs: Prefs): Array<String> =
        (prefs.allowedPackages + appCtx.packageName + essentialTelephonyPackages() +
            settingsPackages() + essentialSupportPackages())
            .distinct().toTypedArray()

    /** Wi-Fi·블루투스 등 설정 패널 진입용 (설치된 것만) */
    private fun settingsPackages(): List<String> =
        listOf("com.android.settings").filter { isInstalled(it) }

    /**
     * 허용 앱이 정상 동작하려면 함께 떠야 하는 시스템 인프라 패키지(설치된 것만).
     * 이게 화이트리스트에 없으면 허용 앱(예: Gemini)이 구글 로그인·계정 선택·권한
     * 요청 화면을 띄우는 순간 lockTask 가 그 화면을 막아 앱이 홈으로 튕긴다.
     * 전부 사용자가 앱을 벗어나 자유롭게 쓸 수 있는 '탈출로'가 아니라 보조 UI/서비스다.
     */
    private fun essentialSupportPackages(): List<String> =
        listOf(
            "com.google.android.gms",              // Google Play 서비스 (계정/인증)
            "com.google.android.gsf",              // Google Services Framework
            "com.google.android.permissioncontroller", // 런타임 권한 요청 화면
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            // Gemini(com.google.android.apps.bard)는 껍데기라 구글앱으로 넘겨 열림 →
            // 이게 없으면 lockTask 가 그 전환을 막아 Gemini 가 열리자마자 닫힘.
            "com.google.android.googlequicksearchbox"
        ).filter { isInstalled(it) }

    /**
     * 잠금 중에도 전화 수·발신이 되도록 항상 화이트리스트에 포함하는 통신 필수 패키지.
     * 통화 화면(InCallUI)·텔레콤·기본 전화앱을 포함하지 않으면 lockTask 가
     * 통화 화면을 막아 전화가 동작하지 않는다.
     */
    private fun essentialTelephonyPackages(): List<String> {
        val pkgs = mutableListOf("com.android.server.telecom")
        runCatching {
            val tm = appCtx.getSystemService(Context.TELECOM_SERVICE)
                    as android.telecom.TelecomManager
            @Suppress("MissingPermission")
            tm.defaultDialerPackage?.let { pkgs.add(it) }
        }
        // 제조사별 통화 화면/전화앱 (설치된 것만)
        listOf(
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.android.dialer",
            "com.google.android.dialer"
        ).forEach { if (isInstalled(it)) pkgs.add(it) }
        return pkgs.distinct()
    }

    private fun isInstalled(pkg: String): Boolean =
        runCatching { appCtx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /**
     * 셋업 확정 시 호출. 모든 키오스크 정책을 적용한다.
     */
    fun applyPolicies(prefs: Prefs) {
        if (!isDeviceOwner()) return

        // 0. 디바이스 오너면 우리 런타임 권한을 자동 부여 → 사용자에게 권한창을 띄우지 않음
        runCatching {
            val perms = mutableListOf<String>()
            if (android.os.Build.VERSION.SDK_INT >= 33) perms += android.Manifest.permission.POST_NOTIFICATIONS
            perms.forEach {
                dpm.setPermissionGrantState(admin, appCtx.packageName, it,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        }

        // 1. lockTask 화이트리스트
        dpm.setLockTaskPackages(admin, lockTaskList(prefs))

        // 2. lockTask 기능: 홈/전원메뉴/시스템정보(시계·배터리)/알림창/기본 잠금화면 허용.
        //    NOTIFICATIONS 포함 → 상단바 내려서 알림 확인 가능.
        //    KEYGUARD 포함 → 화면 켤 때마다 폰 기본 잠금화면이 항상 뜸(일관성).
        //      (예전에 뺐던 이유=잠금화면 오동작방지 필터. 대신 시스템 잠금을 쓰면
        //       StudyLock 자체 잠금이 필요 없어 사용자·관리 모두 단순해짐.)
        dpm.setLockTaskFeatures(
            admin,
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
        )

        // 3. HOME 별칭을 켜고 기본 HOME 으로 고정 → 홈버튼 탈출 차단
        setHomeAliasEnabled(true)
        val homeFilter = IntentFilter(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, homeFilter, homeAlias)

        // 4. 멀티유저 우회 차단 (항상)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_USER_SWITCH)

        // 5. 옵션 정책
        if (prefs.optBlockSafeBoot) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        }
        if (prefs.optBlockTime) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
            dpm.setAutoTimeEnabled(admin, true)
            dpm.setAutoTimeZoneEnabled(admin, true)
        }
        // 상태바는 켜둔다(시계·배터리 표시). 알림창 내리기는 lockTask(NOTIFICATIONS 포함)로 허용.
        dpm.setStatusBarDisabled(admin, false)
    }

    /** 허용앱 추가 후 lockTask 리스트 갱신 */
    fun refreshAllowedApps(prefs: Prefs) {
        if (!isDeviceOwner()) return
        dpm.setLockTaskPackages(admin, lockTaskList(prefs))
    }

    /**
     * 임시(3분) 해제: 키오스크 제약을 풀어 폰을 자유롭게 쓰게 한다.
     * HOME 별칭 끄기 + 영구 HOME 제거 + lockTask 화이트리스트 비움.
     * 재잠금은 applyPolicies() 로 원복.
     */
    fun beginTempUnlock() {
        if (!isDeviceOwner()) return
        setHomeAliasEnabled(false)
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, appCtx.packageName) }
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        runCatching { dpm.setStatusBarDisabled(admin, false) }
    }

    /** device-owner 무확인 앱 삭제 (확인창 없음) */
    fun uninstall(pkg: String) {
        if (!isDeviceOwner()) return
        runCatching {
            val pi = appCtx.packageManager.packageInstaller
            val intent = android.content.Intent("com.studylock.UNINSTALL_DONE").setPackage(appCtx.packageName)
            val pending = android.app.PendingIntent.getBroadcast(
                appCtx, pkg.hashCode(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )
            pi.uninstall(pkg, pending.intentSender)
        }
    }

    fun setAppHidden(pkg: String, hidden: Boolean) {
        if (!isDeviceOwner()) return
        runCatching { dpm.setApplicationHidden(admin, pkg, hidden) }
    }

    /**
     * 스크린타임 적용: 지금 차단해야 할 앱은 숨기고(setApplicationHidden) 나머지는 보이게.
     * 허용앱 전체를 대상으로 idempotent 하게 동기화한다.
     */
    fun applyScreenTime(prefs: Prefs, nowMs: Long, nowMin: Int) {
        if (!isDeviceOwner()) return
        val blocked = ScreenTime.blockedApps(prefs, nowMs, nowMin)
        prefs.allowedPackages.forEach { pkg ->
            runCatching { dpm.setApplicationHidden(admin, pkg, pkg in blocked) }
        }
    }

    /** 집중 잠금: 우리 앱 + 전화만 허용 (다른 앱 전부 차단). 해제는 refreshAllowedApps. */
    fun applyFocusLock() {
        if (!isDeviceOwner()) return
        dpm.setLockTaskPackages(
            admin,
            (listOf(appCtx.packageName) + essentialTelephonyPackages()).distinct().toTypedArray()
        )
    }

    /**
     * 완전 해제: 정책 원복 + Device Owner 권한 반납.
     * 호출 후 앱은 일반 앱으로 돌아간다.
     */
    fun releaseFully(prefs: Prefs) {
        if (!isDeviceOwner()) {
            prefs.clearAll()
            return
        }
        runCatching { dpm.setStatusBarDisabled(admin, false) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_USER_SWITCH) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME) }
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, appCtx.packageName) }
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }

        // 플로팅 일정 서비스 중단
        ScheduleFloatService.stop(appCtx)

        // HOME 별칭을 꺼서 HOME 후보에서 완전히 제거 → 홈버튼이 원래 런처로 복귀
        // (앞 단계에서 예외가 나도 아래 Device Owner 반납은 반드시 실행되도록 전부 runCatching)
        runCatching { setHomeAliasEnabled(false) }
        runCatching { prefs.clearAll() }

        // 마지막: Device Owner 반납 (이후 일반 앱 → 삭제 가능).
        // 이게 실패하면 관리자 앱이라 삭제가 막히므로 가장 확실하게 마지막에 실행.
        runCatching {
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(appCtx.packageName)
        }
    }

    /** 완전 해제 후에도 아직 Device Owner 면(반납 실패) 다시 반납 시도. 삭제 직전 안전장치. */
    fun ensureOwnerCleared(): Boolean {
        if (!isDeviceOwner()) return true
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_USER_SWITCH) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME) }
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        runCatching {
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(appCtx.packageName)
        }
        return !isDeviceOwner()
    }
}
