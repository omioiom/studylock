package com.studylock

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.studylock.ui.PinPanel
import com.studylock.ui.KioskScreen
import com.studylock.ui.TimetableRoot
import com.studylock.ui.FocusSetupScreen
import com.studylock.ui.FocusLockScreen
import com.studylock.ui.TempUnlockScreen
import com.studylock.ui.ScreenTimeRoot
import com.studylock.ui.WhitelistRoot
import com.studylock.ui.SettingsRoot
import com.studylock.ui.PrimaryButton
import com.studylock.ui.SetupFlow
import com.studylock.ui.StudyLockTheme
import com.studylock.ui.Ink
import com.studylock.ui.Gray
import com.studylock.ui.Paper

private enum class Screen { INSTRUCTIONS, SETUP, KIOSK, PIN, TIMETABLE, FOCUS_SETUP, FOCUS_LOCK, TEMP_UNLOCK, SCREENTIME, WHITELIST, SETTINGS, DONE }

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var lock: LockManager

    // 알림 버튼(시간표/집중잠금/스크린타임/설정) → 해당 화면으로 이동
    val gotoState = mutableStateOf<String?>(null)

    // 화면 잠금은 폰 기본 잠금화면(lockTask KEYGUARD)이 담당 — StudyLock 자체 화면잠금 제거.
    // 화면 꺼질 때 사용량 세션만 정산.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) {
                ScreenTime.accrue(prefs, System.currentTimeMillis(), clear = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        lock = LockManager(this)
        com.studylock.ui.AppTheme.dark = prefs.darkMode
        intent?.getStringExtra("goto")?.let { gotoState.value = it }
        runCatching {
            registerReceiver(screenOffReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            })
        }

        // 부팅/재실행 시 정책 재적용 (상태바 차단 등 휘발 대비), lockTask 진입 전에
        if (prefs.locked && lock.isDeviceOwner() && !DateUtil.reached(prefs.targetDateMillis)) {
            if (prefs.tempUnlockActive()) {
                // 임시 해제 유지 — 정책 재적용 안 하고 재잠금 알람만 보장
                scheduleRelock(prefs.tempUnlockUntil)
            } else {
                lock.applyPolicies(prefs)
                // 집중 잠금 중이었다면 허용목록을 다시 우리 앱+전화로 좁힘
                if (prefs.focusLockActive()) lock.applyFocusLock()
            }
            // 스크린타임: 차단 재적용 + 틱 보장
            val t = java.time.LocalTime.now()
            lock.applyScreenTime(prefs, System.currentTimeMillis(), t.hour * 60 + t.minute)
            ScreenTimeReceiver.ensure(this, prefs)
        }

        // 일정 알림: 채널 보장 + 다음 경계 재예약.
        // 디바이스 오너면 applyPolicies 가 알림 권한을 자동 부여하므로 권한창을 띄우지 않음.
        ScheduleNotifier.reschedule(this, prefs)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !lock.isDeviceOwner() &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 55) }
        }

        setContent {
            StudyLockTheme {
                AppRoot(prefs, lock, this)
            }
        }
    }

    /** lockTask 진입 (이미 진입 상태면 무시) */
    fun ensureLockTask() {
        if (!lock.isDeviceOwner() || !prefs.locked) return
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { startLockTask() }
        }
    }

    fun leaveLockTask() {
        runCatching { stopLockTask() }
    }

    // 알림 버튼(goto) 재진입, 또는 홈버튼/런처(ACTION_MAIN) → 메인(KIOSK)으로
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val goto = intent.getStringExtra("goto")
        when {
            goto != null -> gotoState.value = goto
            intent.action == Intent.ACTION_MAIN -> gotoState.value = "kiosk"
        }
    }

    // StudyLock 전면 = 플로팅 숨김 / 다른 앱으로 나가면(onPause) = 표시
    override fun onResume() {
        super.onResume()
        ScheduleFloatService.setForeground(this, true)
    }

    override fun onPause() {
        super.onPause()
        ScheduleFloatService.setForeground(this, false)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    // ---- 임시 해제 재잠금 알람 ----

    private fun relockPendingIntent(): PendingIntent {
        val i = Intent(this, RelockReceiver::class.java).setAction(RelockReceiver.ACTION_RELOCK)
        return PendingIntent.getBroadcast(
            this, 7, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleRelock(at: Long) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = relockPendingIntent()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun cancelRelock() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(relockPendingIntent()) }
    }

    /** 실제 런처로 내보내 폰을 자유롭게 쓰게 한다 (임시 해제용) */
    fun goHomeLauncher() {
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    /** device-owner(기기관리자) 반납 → 일반 앱. 사용자에게 토스트로 알림. */
    fun releaseDeviceOwner(lock: LockManager, prefs: Prefs) {
        runCatching { lock.releaseFully(prefs) }   // device-owner 반납(삭제 가능해짐)
        android.widget.Toast.makeText(
            this, "기기관리자 권한이 해제되었어요. 이제 앱을 삭제할 수 있어요.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    /** 앱 삭제 화면 띄우기 (device-owner 반납 후에만 실제로 삭제됨) */
    fun uninstallSelf(lock: LockManager) {
        // 1) 아직 기기관리자면 삭제가 막히므로 반납 재시도 (releaseFully 중 일부 실패 대비)
        if (!lock.ensureOwnerCleared()) {
            android.widget.Toast.makeText(
                this, "기기관리자 해제에 실패했어요. 설정 > 기기 관리 앱에서 StudyLock 을 끈 뒤 삭제해주세요.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        val uri = android.net.Uri.parse("package:$packageName")
        // 2) 표준 제거 화면 시도
        val ok = runCatching {
            startActivity(Intent(Intent.ACTION_DELETE, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        // 3) 실패하면 앱 정보 화면으로 폴백 (여기서 '삭제' 직접 가능)
        if (!ok) {
            runCatching {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

@Composable
private fun AppRoot(prefs: Prefs, lock: LockManager, activity: MainActivity) {
    var screen by remember { mutableStateOf(initialScreen(prefs, lock)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var stLimitPkg by remember { mutableStateOf<String?>(null) }   // 앱 롱프레스 → 하루제한 프리셋

    // 알림 버튼 → 해당 화면으로 이동
    val goto by activity.gotoState
    LaunchedEffect(goto) {
        if (goto != null) {
            // 집중잠금 중엔 홈버튼·알림버튼으로 잠금화면을 벗어날 수 없게 (탈출 방지)
            screen = when {
                prefs.focusLockActive() -> Screen.FOCUS_LOCK
                goto == "timetable" -> Screen.TIMETABLE
                goto == "focus" -> Screen.FOCUS_SETUP
                goto == "screentime" -> Screen.SCREENTIME
                goto == "settings" -> Screen.SETTINGS
                else -> initialScreen(prefs, lock)   // "kiosk"(홈버튼) = 상태에 맞는 기본화면
            }
            activity.gotoState.value = null
        }
    }

    // 화면 복귀마다 상태 재평가 (D-day 갱신, 목표일 도달 시 자동 해제, lockTask 보장)
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                val owned = prefs.locked && lock.isDeviceOwner()
                when {
                    owned && DateUtil.reached(prefs.targetDateMillis) -> {
                        activity.leaveLockTask()
                        lock.releaseFully(prefs)
                        screen = Screen.DONE
                    }
                    // 예약된 스터디락 해제 창 진입
                    tryScheduledUnlock(prefs, lock, activity) -> {
                        screen = Screen.TEMP_UNLOCK
                    }
                    // 임시 해제 만료 → 재잠금
                    owned && prefs.tempUnlockUntil > 0 && !prefs.tempUnlockActive() -> {
                        prefs.tempUnlockUntil = 0L
                        activity.cancelRelock()
                        lock.applyPolicies(prefs)
                        val t = java.time.LocalTime.now()
                        lock.applyScreenTime(prefs, System.currentTimeMillis(), t.hour * 60 + t.minute)
                        ScreenTimeReceiver.ensure(activity, prefs)
                        activity.ensureLockTask()
                        screen = Screen.KIOSK
                    }
                    // 임시 해제 진행 중 → 카운트다운 화면 (잠그지 않음)
                    owned && prefs.tempUnlockActive() -> {
                        screen = Screen.TEMP_UNLOCK
                    }
                    // 집중 잠금 진행 중 → 무조건 잠금화면 유지 (앱 전환 우회 차단)
                    owned && prefs.focusLockActive() -> {
                        lock.applyFocusLock()
                        activity.ensureLockTask()
                        screen = Screen.FOCUS_LOCK
                    }
                    // 집중 잠금이 만료된 채 잠금화면에 있으면 원복
                    owned && screen == Screen.FOCUS_LOCK -> {
                        prefs.focusLockUntil = 0L
                        lock.refreshAllowedApps(prefs)
                        activity.ensureLockTask()
                        screen = Screen.KIOSK
                    }
                    screen == Screen.KIOSK -> {
                        lock.applyPolicies(prefs)   // 부팅 후 휘발된 정책 재적용 (idempotent)
                        activity.ensureLockTask()
                        prefs.lastApp = ""          // 홈으로 왔으니 복귀 대상 비움
                        // 앱에서 홈으로 복귀 → 사용 세션 정산 + 스크린타임 적용
                        ScreenTime.accrue(prefs, System.currentTimeMillis(), clear = true)
                        val t = java.time.LocalTime.now()
                        lock.applyScreenTime(prefs, System.currentTimeMillis(), t.hour * 60 + t.minute)
                        ScreenTimeReceiver.ensure(activity, prefs)
                    }
                }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    when (screen) {
        Screen.INSTRUCTIONS -> InstructionsScreen()

        Screen.SETUP -> SetupFlow(
            prefs = prefs,
            onQuit = {                                    // 시작 전엔 삭제 자유
                activity.releaseDeviceOwner(lock, prefs)   // 기기관리자 반납 + 토스트 알림
                screen = Screen.DONE                       // 안내 화면으로 (앱 삭제 버튼 있음)
            },
            onActivate = { result ->
                prefs.targetDateMillis = result.targetMillis
                prefs.ddayLabel = result.ddayLabel
                prefs.allowedPackages = result.allowed
                prefs.optBlockTime = result.blockTime
                prefs.optBlockSafeBoot = result.blockSafeBoot
                prefs.optBlockStatusBar = result.blockStatusBar
                prefs.setPin(result.pin)
                prefs.locked = true
                lock.applyPolicies(prefs)
                activity.ensureLockTask()
                screen = Screen.KIOSK
            }
        )

        Screen.KIOSK -> {
            BackHandler(enabled = true) { /* 잠금 중 뒤로가기 무시 */ }
            KioskScreen(
                prefs = prefs,
                refreshKey = refreshKey,
                onLaunchApp = { pkg ->
                    // 사용 세션 시작(이전 세션 정산) → 스크린타임 추적
                    ScreenTime.startSession(prefs, pkg, System.currentTimeMillis())
                    ScreenTimeReceiver.ensure(activity, prefs)
                    prefs.lastApp = pkg   // 잠금 풀면 이 앱으로 복귀하도록 기억
                    AppCatalog.launchIntent(activity, pkg)?.let { activity.startActivity(it) }
                },
                onOpenTimetable = { screen = Screen.TIMETABLE },
                onOpenFocusLock = { screen = Screen.FOCUS_SETUP },
                onOpenScreenTime = { screen = Screen.SCREENTIME },
                onOpenWhitelist = { screen = Screen.WHITELIST },
                onOpenSettings = { screen = Screen.SETTINGS },
                onExcludeApp = { pkg ->
                    prefs.allowedPackages = prefs.allowedPackages - pkg
                    lock.refreshAllowedApps(prefs)   // lockTask 화이트리스트 갱신
                    refreshKey++                      // 그리드 새로고침
                },
                onUninstallApp = { pkg ->
                    lock.uninstall(pkg)               // device-owner 무확인 삭제
                    prefs.allowedPackages = prefs.allowedPackages - pkg
                    lock.refreshAllowedApps(prefs)
                    refreshKey++
                },
                onLimitApp = { pkg -> stLimitPkg = pkg; screen = Screen.SCREENTIME }
            )
        }

        Screen.TIMETABLE -> {
            // 시간표 화면. 자체 BackHandler 로 닫기 처리. 닫을 때 홈(현재 할 일) 재구성
            TimetableRoot(prefs = prefs, onClose = { refreshKey++; screen = Screen.KIOSK })
        }

        Screen.SCREENTIME -> ScreenTimeRoot(
            prefs = prefs, lock = lock,
            onClose = { stLimitPkg = null; screen = Screen.KIOSK },
            initialLimitPkg = stLimitPkg
        )

        Screen.WHITELIST -> WhitelistRoot(
            prefs = prefs, lock = lock, onClose = { screen = Screen.KIOSK }
        )

        Screen.SETTINGS -> SettingsRoot(
            prefs = prefs,
            onClose = { screen = Screen.KIOSK },
            onOpenManage = { screen = Screen.PIN }
        )

        Screen.FOCUS_SETUP -> FocusSetupScreen(
            prefs = prefs,
            onStart = { untilMs ->
                prefs.focusLockUntil = untilMs
                lock.applyFocusLock()          // 우리 앱+전화만 허용 (나머지 차단)
                activity.ensureLockTask()
                screen = Screen.FOCUS_LOCK
            },
            onCancel = { screen = Screen.KIOSK }
        )

        Screen.FOCUS_LOCK -> FocusLockScreen(
            prefs = prefs,
            onExpire = {
                prefs.focusLockUntil = 0L
                lock.refreshAllowedApps(prefs)  // 허용목록 원복
                screen = Screen.KIOSK
            },
            onUnlock = {
                prefs.focusLockUntil = 0L
                lock.refreshAllowedApps(prefs)
                screen = Screen.KIOSK
            }
        )

        Screen.PIN -> {
            BackHandler(enabled = true) { screen = Screen.KIOSK }
            PinPanel(
                prefs = prefs,
                lockManager = lock,
                onClose = { screen = Screen.KIOSK },
                onAppsChanged = { refreshKey++ },
                onTempUnlock = {
                    val until = System.currentTimeMillis() + 3 * 60_000L
                    prefs.tempUnlockUntil = until
                    activity.leaveLockTask()       // lockTask 종료
                    lock.beginTempUnlock()         // 키오스크 제약 풀기
                    // 해제 중엔 스크린타임 숨김도 즉시 풀기 (tempUnlockActive → 전체 복원)
                    val t = java.time.LocalTime.now()
                    lock.applyScreenTime(prefs, System.currentTimeMillis(), t.hour * 60 + t.minute)
                    activity.scheduleRelock(until) // 3분 뒤 재잠금 알람
                    activity.goHomeLauncher()      // 실제 런처로 내보내 자유 사용
                    screen = Screen.TEMP_UNLOCK
                },
                onReleased = {
                    activity.leaveLockTask()          // 1. lockTask 먼저 종료
                    lock.releaseFully(prefs)           // 2. 정책 원복 + 권한 반납
                    screen = Screen.DONE
                }
            )
        }

        Screen.TEMP_UNLOCK -> TempUnlockScreen(
            prefs = prefs,
            onRelockNow = {
                prefs.scheduledUnlockSkip = prefs.tempUnlockUntil  // 예약창이면 그 창 재해제 방지
                prefs.tempUnlockUntil = 0L
                activity.cancelRelock()
                lock.applyPolicies(prefs)
                // 스크린타임 즉시 재적용 — 이 시간에 걸린 앱별 제한/시간대 차단 복원
                val t = java.time.LocalTime.now()
                lock.applyScreenTime(prefs, System.currentTimeMillis(), t.hour * 60 + t.minute)
                ScreenTimeReceiver.ensure(activity, prefs)
                activity.ensureLockTask()
                screen = Screen.KIOSK
            },
            onContinue = { activity.goHomeLauncher() }
        )

        Screen.DONE -> DoneScreen(
            onExit = { activity.finishAndRemoveTask() },
            onDelete = { activity.uninstallSelf(lock) }   // 반납 재확인 후 삭제화면
        )
    }
}

private fun millisAtMinToday(min: Int): Long {
    val day = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
    return day.toInstant().toEpochMilli() + min * 60_000L
}

/** 예약 '스터디락 전체 해제' 창에 들어왔으면 임시해제 시작. 시작하면 true */
private fun tryScheduledUnlock(prefs: Prefs, lock: LockManager, activity: MainActivity): Boolean {
    if (!(prefs.locked && lock.isDeviceOwner())) return false
    if (DateUtil.reached(prefs.targetDateMillis)) return false
    val t = java.time.LocalTime.now()
    val nowMin = t.hour * 60 + t.minute
    val w = ScreenTime.activeUnlockWindow(prefs, nowMin) ?: return false
    // 자정 넘기는 창(예: 23:35~01:00)에서 저녁쪽이면 종료는 '내일' 새벽
    val crosses = w.endMin <= w.startMin
    val endMs = if (crosses && nowMin >= w.startMin) millisAtMinToday(w.endMin) + 86_400_000L
                else millisAtMinToday(w.endMin)
    if (endMs <= System.currentTimeMillis()) return false
    if (prefs.tempUnlockUntil >= endMs) return false
    if (prefs.scheduledUnlockSkip == endMs) return false
    prefs.tempUnlockUntil = endMs
    activity.leaveLockTask()
    lock.beginTempUnlock()
    // 전체 해제 창: 스크린타임 숨김도 즉시 풀기 (tempUnlockActive → 전체 복원)
    val lt = java.time.LocalTime.now()
    lock.applyScreenTime(prefs, System.currentTimeMillis(), lt.hour * 60 + lt.minute)
    activity.scheduleRelock(endMs)
    activity.goHomeLauncher()
    return true
}

private fun initialScreen(prefs: Prefs, lock: LockManager): Screen = when {
    !lock.isDeviceOwner() -> if (prefs.locked) Screen.DONE else Screen.INSTRUCTIONS
    !prefs.locked -> Screen.SETUP
    DateUtil.reached(prefs.targetDateMillis) -> Screen.DONE
    prefs.tempUnlockActive() -> Screen.TEMP_UNLOCK
    prefs.focusLockActive() -> Screen.FOCUS_LOCK
    else -> Screen.KIOSK
}

@Composable
private fun InstructionsScreen() {
    Column(
        Modifier.fillMaxSize().background(Paper).padding(28.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text("기기 설정 필요", style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp), color = Ink)
        Spacer(Modifier.height(16.dp))
        Text(
            "이 앱은 기기 관리자(Device Owner) 권한이 있어야 동작합니다.\n\n" +
                "1. 공장초기화 후 구글 계정을 추가하지 않은 상태에서\n" +
                "2. USB 디버깅을 켜고 PC 에 연결한 뒤\n" +
                "3. 아래 명령을 1회 실행하세요.",
            style = MaterialTheme.typography.bodyLarge, color = Gray
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.background(Ink, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(16.dp)
        ) {
            Text(
                "adb shell dpm set-device-owner\ncom.studylock/.AdminReceiver",
                color = Paper, fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "명령 실행 후 앱을 다시 열면 설정이 시작됩니다.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun DoneScreen(onExit: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text("해제 완료", style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp), color = Ink)
        Spacer(Modifier.height(12.dp))
        Text("기기관리자 권한이 해제되어 기기가 정상 상태로 돌아왔습니다.\n이제 앱을 삭제하거나 종료할 수 있어요.",
            style = MaterialTheme.typography.bodyLarge, color = Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        PrimaryButton("종료") { onExit() }
        Spacer(Modifier.height(6.dp))
        Text(
            "앱 삭제", style = MaterialTheme.typography.bodyMedium, color = Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDelete).padding(vertical = 14.dp)
        )
    }
}
