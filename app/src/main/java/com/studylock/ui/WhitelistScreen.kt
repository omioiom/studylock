package com.studylock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylock.AllowWindow
import com.studylock.LockManager
import com.studylock.Prefs
import com.studylock.ScreenTime
import com.studylock.ScreenTimeReceiver
import com.studylock.ScreenTimeRules
import com.studylock.UnlockWindow

private enum class WlSub { MAIN, ADD_ALLOW, ADD_UNLOCK, PIN_ALLOW, PIN_UNLOCK }

private fun wlHhmm(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)
private fun wlAppLabel(context: android.content.Context, pkg: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)).toString()
    }.getOrDefault(pkg)

@Composable
fun WhitelistRoot(prefs: Prefs, lock: LockManager, onClose: () -> Unit) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(ScreenTime.parseRules(prefs.screenTimeJson)) }
    var sub by remember { mutableStateOf(WlSub.MAIN) }

    fun persist(r: ScreenTimeRules) {
        rules = r
        prefs.screenTimeJson = ScreenTime.serialize(r)
        val t = java.time.LocalTime.now()
        lock.applyScreenTime(prefs, System.currentTimeMillis(), t.hour * 60 + t.minute)
        ScreenTimeReceiver.ensure(context, prefs)
    }

    when (sub) {
        WlSub.MAIN -> WlMain(prefs, context, rules,
            onClose = onClose,
            onAddAllow = { sub = WlSub.PIN_ALLOW },
            onAddUnlock = { sub = WlSub.PIN_UNLOCK },
            onDeleteAllow = { persist(rules.copy(allowWindows = rules.allowWindows - it)) },
            onDeleteUnlock = { persist(rules.copy(unlockWindows = rules.unlockWindows - it)) })

        WlSub.PIN_ALLOW -> WlPinGate(prefs::verifyPin, { sub = WlSub.ADD_ALLOW }, { sub = WlSub.MAIN })
        WlSub.PIN_UNLOCK -> WlPinGate(prefs::verifyPin, { sub = WlSub.ADD_UNLOCK }, { sub = WlSub.MAIN })

        WlSub.ADD_ALLOW -> AddAllowScreen(prefs, context,
            onBack = { sub = WlSub.MAIN },
            onSave = { pkg, s, e, days ->
                val now = System.currentTimeMillis()
                persist(rules.copy(allowWindows = rules.allowWindows + AllowWindow(now, pkg, s, e, now, days)))
                sub = WlSub.MAIN
            })
        WlSub.ADD_UNLOCK -> AddUnlockScreen(prefs,
            onBack = { sub = WlSub.MAIN },
            onSave = { s, e, days ->
                val now = System.currentTimeMillis()
                persist(rules.copy(unlockWindows = rules.unlockWindows + UnlockWindow(now, s, e, now, days)))
                sub = WlSub.MAIN
            })
    }
}

@Composable
private fun WlMain(
    prefs: Prefs, context: android.content.Context, rules: ScreenTimeRules,
    onClose: () -> Unit, onAddAllow: () -> Unit, onAddUnlock: () -> Unit,
    onDeleteAllow: (AllowWindow) -> Unit, onDeleteUnlock: (UnlockWindow) -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    var pendingDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    pendingDelete?.let { (title, doDelete) ->
        SlideConfirmDialog(
            title = "삭제",
            message = "'$title' 규칙을 삭제해요.",
            onConfirm = { doDelete(); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("화이트리스트", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
            Spacer(Modifier.weight(1f))
            Text("닫기", color = Gray, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("정해둔 시간에만 앱·잠금을 열어둡니다. 추가엔 복구코드가 필요하고(푸는 거라), 삭제는 자유예요.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(22.dp))

        SectionT("스터디락 전체 해제 시간")
        rules.unlockWindows.forEach { w ->
            val t = "${wlHhmm(w.startMin)}–${wlHhmm(w.endMin)}"
            val d = if (prefs.timetablePerDay) "  · ${ScreenTime.daysLabel(w.days)}" else ""
            WlRow(t, "이 시간엔 잠금 전체 해제$d") { pendingDelete = t to { onDeleteUnlock(w) } }
        }
        AddR("+ 스터디락 해제 시간 추가", onAddUnlock)

        Spacer(Modifier.height(24.dp))
        SectionT("앱 시간대 허용")
        rules.allowWindows.forEach { w ->
            val t = "${wlAppLabel(context, w.pkg)} · ${wlHhmm(w.startMin)}–${wlHhmm(w.endMin)}"
            val d = if (prefs.timetablePerDay) "  · ${ScreenTime.daysLabel(w.days)}" else ""
            WlRow(t, "이 시간 외엔 차단$d") { pendingDelete = t to { onDeleteAllow(w) } }
        }
        AddR("+ 앱 허용 시간 추가", onAddAllow)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable private fun SectionT(t: String) {
    Text(t, color = Ink, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(10.dp))
}

@Composable
private fun WlRow(title: String, sub: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.dp, GrayLight, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp)); Text(sub, style = MaterialTheme.typography.bodyMedium)
        }
        Text("삭제", color = Gray, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable private fun AddR(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, style = MaterialTheme.typography.labelLarge)
    }
}

// ---- 앱 허용 추가 ----
@Composable
private fun AddAllowScreen(prefs: Prefs, context: android.content.Context,
                           onBack: () -> Unit, onSave: (String, Int, Int, Int) -> Unit) {
    BackHandler(enabled = true) { onBack() }
    var pkg by remember { mutableStateOf<String?>(null) }
    var start by remember { mutableIntStateOf(0) }
    var end by remember { mutableIntStateOf(30) }
    var selDays by remember { mutableStateOf(todayDaySet()) }
    val apps = remember { prefs.allowedPackages.sortedBy { wlAppLabel(context, it).lowercase() } }
    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("앱 허용 시간", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("이 시간대에만 켤 수 있고, 그 외엔 차단돼요.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        Row { WlTimeStepper("시작", start) { start = it }; Spacer(Modifier.width(12.dp)); WlTimeStepper("종료", end) { end = it } }
        if (prefs.timetablePerDay) { Spacer(Modifier.height(16.dp)); DayPicker(selDays) { selDays = it } }
        Spacer(Modifier.height(16.dp))
        Text("앱 선택", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            LazyColumn {
                items(apps) { p ->
                    val on = p == pkg
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) Ink else Paper, RoundedCornerShape(12.dp))
                        .clickable { pkg = p }.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(wlAppLabel(context, p), color = if (on) Paper else Ink,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Filled.Check, null, tint = Paper, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        WlButton("저장", enabled = pkg != null && end > start && selDays.isNotEmpty()) { pkg?.let { onSave(it, start, end, daysMask(selDays)) } }
        WlBack(onBack); Spacer(Modifier.height(16.dp))
    }
}

// ---- 스터디락 해제 추가 ----
@Composable
private fun AddUnlockScreen(prefs: Prefs, onBack: () -> Unit, onSave: (Int, Int, Int) -> Unit) {
    BackHandler(enabled = true) { onBack() }
    var start by remember { mutableIntStateOf(0) }
    var end by remember { mutableIntStateOf(30) }
    var selDays by remember { mutableStateOf(todayDaySet()) }
    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("스터디락 해제 시간", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("이 시간대엔 잠금이 전부 풀려 폰을 자유롭게 써요.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Row { WlTimeStepper("시작", start) { start = it }; Spacer(Modifier.width(16.dp)); WlTimeStepper("종료", end) { end = it } }
        if (prefs.timetablePerDay) { Spacer(Modifier.height(20.dp)); DayPicker(selDays) { selDays = it } }
        Spacer(Modifier.weight(1f))
        WlButton("저장", enabled = end > start && selDays.isNotEmpty()) { onSave(start, end, daysMask(selDays)) }
        WlBack(onBack); Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WlTimeStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Gray, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WlChip("−") { onChange((value - 10 + 1440) % 1440) }
            Text(wlHhmm(value), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            WlChip("+") { onChange((value + 10) % 1440) }
        }
    }
}

@Composable private fun WlChip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Paper, RoundedCornerShape(12.dp)).border(1.dp, GrayLight, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun WlButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) Ink else GrayLight, RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Paper, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun WlBack(onBack: () -> Unit) {
    Spacer(Modifier.height(4.dp))
    Text("뒤로", color = Gray, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(14.dp), textAlign = TextAlign.Center)
}

@Composable
private fun WlPinGate(verify: (String) -> Boolean, onSuccess: () -> Unit, onCancel: () -> Unit) {
    BackHandler(enabled = true) { onCancel() }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(entered) {
        if (entered.length == Prefs.PIN_LEN) {
            if (verify(entered)) onSuccess()
            else { error = true; kotlinx.coroutines.delay(700); entered = ""; error = false }
        }
    }
    Column(Modifier.fillMaxSize().background(Paper).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(64.dp))
        Text("복구코드 입력", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(if (error) "코드가 일치하지 않습니다" else "허용/해제 추가엔 복구코드가 필요해요",
            style = MaterialTheme.typography.bodyMedium, color = if (error) Ink else Gray)
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            PinBoxes(text = entered, error = error)
            BasicTextField(value = entered, onValueChange = { entered = Prefs.normalizePinInput(it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                cursorBrush = SolidColor(Color.Transparent), textStyle = TextStyle(color = Color.Transparent),
                modifier = Modifier.matchParentSize().focusRequester(focus))
        }
        Spacer(Modifier.weight(1f))
        Text("취소", color = Gray, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable(onClick = onCancel).padding(12.dp))
        Spacer(Modifier.height(16.dp))
    }
}
