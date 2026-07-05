package com.studylock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.studylock.QuickActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylock.Prefs

private enum class SetSub { MAIN }

@Composable
fun SettingsRoot(prefs: Prefs, onClose: () -> Unit, onOpenManage: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun openSetting(action: String) {
        runCatching {
            context.startActivity(android.content.Intent(action)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun openHotspot() {
        runCatching {
            context.startActivity(android.content.Intent()
                .setClassName("com.android.settings", "com.android.settings.TetherSettings")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { openSetting(android.provider.Settings.ACTION_WIRELESS_SETTINGS) }
    }
    var sub by remember { mutableStateOf(SetSub.MAIN) }
    var version by remember { mutableStateOf(0) }   // 저장 후 갱신 트리거
    var homeDday by remember { mutableIntStateOf(prefs.homeDday) }
    BackHandler(enabled = true) { if (sub == SetSub.MAIN) onClose() else sub = SetSub.MAIN }

    when (sub) {
        SetSub.MAIN -> {
            Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("설정", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("닫기", color = Gray, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
                }

                Spacer(Modifier.height(20.dp))
                Text("홈 화면 D-day", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text("'작게'로 하면 그 자리에 지금 할 일이 크게 뜨고 D-day 는 작아져요.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LockChoice("크게", homeDday == 0, Modifier.weight(1f)) { homeDday = 0; prefs.homeDday = 0 }
                    LockChoice("작게", homeDday == 1, Modifier.weight(1f)) { homeDday = 1; prefs.homeDday = 1 }
                }

                Spacer(Modifier.height(20.dp))
                Text("화면 테마", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(10.dp))
                ToggleRow("다크 모드", "어두운 배경으로 전환", AppTheme.dark) {
                    AppTheme.dark = it; prefs.darkMode = it
                }

                Spacer(Modifier.height(28.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
                Spacer(Modifier.height(20.dp))

                // ---- 진행 중 일정 알림 ----
                Text("진행 중 일정 알림", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text("진행 중인 일정을 알림으로 띄워요. 알림 패널을 열면 진행바·다음 일정·D-day 와 바로가기 버튼(시간표·집중잠금·스크린타임·설정)이 보여요.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                var floatOn by remember { mutableStateOf(prefs.floatingSchedule) }
                ToggleRow("진행 중 일정 알림", "진행바 · 다음 일정 · 바로가기", floatOn) { on ->
                    floatOn = on; prefs.floatingSchedule = on
                    if (on) com.studylock.ScheduleFloatService.start(context)
                    else com.studylock.ScheduleFloatService.stop(context)
                }

                Spacer(Modifier.height(22.dp))

                // ---- 일정 시작 알림 진동 ----
                Text("일정 시작 알림 진동", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(10.dp))
                var vibe by remember { mutableIntStateOf(prefs.scheduleVibeMode) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LockChoice("끄기", vibe == 0, Modifier.weight(1f)) { vibe = 0; prefs.scheduleVibeMode = 0 }
                    LockChoice("짧게", vibe == 1, Modifier.weight(1f)) { vibe = 1; prefs.scheduleVibeMode = 1 }
                    LockChoice("길게", vibe == 2, Modifier.weight(1f)) { vibe = 2; prefs.scheduleVibeMode = 2 }
                }

                Spacer(Modifier.height(28.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
                Spacer(Modifier.height(20.dp))

                Text("빠른 설정", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(10.dp))

                @Suppress("UNUSED_EXPRESSION") version   // 권한/상태 갱신 트리거
                val canWrite = QuickActions.canWriteSettings(context)
                if (!canWrite) {
                    Box(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink, RoundedCornerShape(14.dp))
                        .clickable { QuickActions.openWriteSettings(context); version++ }
                        .padding(horizontal = 18.dp, vertical = 15.dp)) {
                        Text("밝기·자동회전을 조절하려면 ‘설정 수정’ 권한을 켜주세요  →",
                            color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 밝기 슬라이더
                BrightnessControl()
                Spacer(Modifier.height(10.dp))
                // 밝기 최적화(자동 밝기)
                ToggleRow("밝기 최적화", "주변 밝기에 맞춰 자동 조절",
                    QuickActions.isAutoBrightness(context)) { QuickActions.setAutoBrightness(context, it); version++ }
                Spacer(Modifier.height(10.dp))
                // 화면 자동 회전
                ToggleRow("화면 자동 회전", "기기를 돌리면 화면도 회전",
                    QuickActions.isAutoRotate(context)) { QuickActions.setAutoRotate(context, it); version++ }

                Spacer(Modifier.height(16.dp))
                // 시스템 설정 바로가기
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickSetting("Wi-Fi", Modifier.weight(1f)) { openSetting("android.settings.panel.action.WIFI") }
                    QuickSetting("데이터", Modifier.weight(1f)) { openSetting("android.settings.panel.action.INTERNET_CONNECTIVITY") }
                    QuickSetting("블루투스", Modifier.weight(1f)) { openSetting(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickSetting("방해금지", Modifier.weight(1f)) { openSetting("android.settings.ZEN_MODE_SETTINGS") }
                    QuickSetting("설정", Modifier.weight(1f)) { openSetting(android.provider.Settings.ACTION_SETTINGS) }
                }

                Spacer(Modifier.height(28.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
                Spacer(Modifier.height(20.dp))

                Text("관리", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, GrayLight, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenManage).padding(18.dp)) {
                    Text("관리 메뉴", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(3.dp))
                    Text("허용 앱 추가 · 3분 해제 · 완전 해제 (복구코드 필요)",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun BrightnessControl() {
    val ctx = LocalContext.current
    var frac by remember { mutableStateOf(QuickActions.getBrightness(ctx) / 255f) }
    fun set(f: Float) {
        frac = f.coerceIn(0f, 1f)
        QuickActions.setBrightness(ctx, (4 + frac * 251).toInt())
    }
    Row(Modifier.fillMaxWidth()
        .border(1.dp, GrayLight, RoundedCornerShape(14.dp))
        .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("밝기", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 16.dp))
        Box(Modifier.weight(1f).height(30.dp)
            .clip(RoundedCornerShape(15.dp)).background(GrayField)
            .pointerInput(Unit) { detectTapGestures { set(it.x / size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { c, _ -> set(c.position.x / size.width) }
            },
            contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0.05f, 1f))
                .clip(RoundedCornerShape(15.dp)).background(Ink))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .border(1.dp, GrayLight, RoundedCornerShape(14.dp))
        .clickable { onToggle(!on) }
        .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(12.dp))
        BwSwitch(on)
    }
}

@Composable
private fun BwSwitch(on: Boolean) {
    Box(Modifier.size(46.dp, 28.dp).clip(RoundedCornerShape(50))
        .background(if (on) Ink else GrayField)
        .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(50))
        .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Paper)
            .then(if (on) Modifier else Modifier.border(1.dp, GrayLight, CircleShape)))
    }
}

@Composable
private fun QuickSetting(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier
        .clip(RoundedCornerShape(12.dp))
        .border(1.dp, GrayLight, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Ink, fontSize = 14.sp)
    }
}

@Composable
private fun LockChoice(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier
        .clip(RoundedCornerShape(12.dp))
        .background(if (on) Ink else Paper, RoundedCornerShape(12.dp))
        .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (on) Paper else Ink, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
    }
}

// ---- PIN 설정 (입력 → 확인) ----

@Composable
private fun SetPinScreen(onCancel: () -> Unit, onDone: (String) -> Unit) {
    BackHandler(enabled = true) { onCancel() }
    var first by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("새 PIN (4자리 이상)") }

    Column(Modifier.fillMaxSize().background(Paper).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(64.dp))
        Text(if (first == null) "PIN 설정" else "PIN 확인", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(msg, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(entered.length.coerceAtLeast(4)) { i ->
                Box(Modifier.size(16.dp)
                    .background(if (i < entered.length) Ink else Paper, RoundedCornerShape(8.dp))
                    .border(1.5.dp, GrayLight, RoundedCornerShape(8.dp)))
            }
        }
        Spacer(Modifier.height(28.dp))
        NumKeypad(
            onDigit = { if (entered.length < 12) entered += it },
            onDelete = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
            onOk = {
                if (entered.length < 4) { msg = "4자리 이상 입력하세요" }
                else if (first == null) { first = entered; entered = ""; msg = "한 번 더 입력" }
                else if (first == entered) onDone(entered)
                else { first = null; entered = ""; msg = "일치하지 않음. 다시 새 PIN 입력" }
            }
        )
        Spacer(Modifier.weight(1f))
        Text("취소", color = Gray, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable(onClick = onCancel).padding(12.dp))
    }
}

// ---- 패턴 설정 (그리기 → 확인) ----

@Composable
private fun SetPatternScreen(onCancel: () -> Unit, onDone: (String) -> Unit) {
    BackHandler(enabled = true) { onCancel() }
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("새 패턴 (4점 이상)") }

    Column(Modifier.fillMaxSize().background(Paper).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Text(if (first == null) "패턴 설정" else "패턴 확인", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(msg, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        PatternPad { drawn ->
            if (first == null) { first = drawn; msg = "한 번 더 그리기" }
            else if (first == drawn) onDone(drawn)
            else { first = null; msg = "일치하지 않음. 다시 새 패턴 그리기" }
        }
        Spacer(Modifier.weight(1f))
        Text("취소", color = Gray, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable(onClick = onCancel).padding(12.dp))
    }
}
