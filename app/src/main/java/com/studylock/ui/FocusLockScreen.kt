package com.studylock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylock.Prefs
import com.studylock.TimetableLoader
import kotlinx.coroutines.delay

// ============================ 집중 잠금 설정 ============================

private val DURATIONS = listOf(30, 60, 90, 120, 180)

private fun curMin(): Int {
    val t = java.time.LocalTime.now()
    return t.hour * 60 + t.minute
}

/** 오늘(또는 이미 지났으면 내일) HH:MM:00 의 정확한 epoch. 알림 경계와 일치시키기 위함. */
private fun blockEndEpoch(endMin: Int): Long {
    val c = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, (endMin / 60) % 24)
        set(java.util.Calendar.MINUTE, endMin % 60)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    if (c.timeInMillis <= System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

@Composable
fun FocusSetupScreen(prefs: Prefs, onStart: (Long) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var minutes by remember { mutableStateOf(60) }
    var useSchedule by remember { mutableStateOf(false) }
    BackHandler(enabled = true) { onCancel() }

    // 현재 진행 중인 일정 (있으면 '끝까지 잠금' 가능)
    val timetable = remember {
        TimetableLoader.parse(TimetableLoader.activeJson(context, prefs)).getOrNull()
    }
    val nowMin = remember { curMin() }
    val curBlock = timetable?.blocks?.let { bs ->
        TimetableLoader.currentIndex(bs, nowMin).takeIf { it >= 0 }?.let { bs[it] }
    }
    val schedRemain = curBlock?.let { (it.endMin - curMin()).coerceAtLeast(1) }

    Column(
        Modifier.fillMaxSize().background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("집중 잠금", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "설정한 시간 동안 모든 앱이 완전히 잠깁니다.\n시간이 끝나거나 복구코드(PIN)를 입력해야 풀립니다.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        // ---- 현재 일정 끝까지 ----
        if (curBlock != null && schedRemain != null) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (useSchedule) Ink else Paper, RoundedCornerShape(16.dp))
                    .border(1.dp, if (useSchedule) Ink else GrayLight, RoundedCornerShape(16.dp))
                    .clickable { useSchedule = true }
                    .padding(18.dp)
            ) {
                Column {
                    Text("현재 일정 끝까지",
                        color = if (useSchedule) Paper else Ink,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("${curBlock.content} · ${curBlock.end}까지 (${fmtMinutes(schedRemain)})",
                        color = if (useSchedule) GrayLight else Gray, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("또는 직접 설정", color = Gray, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }

        // ---- 프리셋 ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DURATIONS.take(3).forEach { DurationChip(it, !useSchedule && minutes == it) { minutes = it; useSchedule = false } }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DURATIONS.drop(3).forEach { DurationChip(it, !useSchedule && minutes == it) { minutes = it; useSchedule = false } }
        }

        // ---- 커스텀 스테퍼 ----
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepBtn("−10") { minutes = (minutes - 10).coerceAtLeast(5); useSchedule = false }
            StepBtn("−5") { minutes = (minutes - 5).coerceAtLeast(5); useSchedule = false }
            StepBtn("+5") { minutes = (minutes + 5).coerceAtMost(600); useSchedule = false }
            StepBtn("+10") { minutes = (minutes + 10).coerceAtMost(600); useSchedule = false }
        }

        // ---- 현재 선택 표시 ----
        Spacer(Modifier.height(16.dp))
        if (useSchedule && schedRemain != null) {
            Text("${curBlock?.end}까지", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("${fmtMinutes(schedRemain)} 동안 잠금", color = Gray, fontSize = 14.sp)
        } else {
            Text(fmtMinutes(minutes), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("동안 잠금", color = Gray, fontSize = 14.sp)
        }

        Spacer(Modifier.height(18.dp))
        SlideToConfirm(
            text = "밀어서 집중 잠금 시작",
            onConfirm = {
                onStart(
                    if (useSchedule && curBlock != null) blockEndEpoch(curBlock.endMin)   // 정확한 일정 끝 시각
                    else System.currentTimeMillis() + minutes * 60_000L                    // 직접 설정한 시간
                )
            }
        )
        Spacer(Modifier.height(4.dp))
        Text("취소", color = Gray, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onCancel).padding(12.dp),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(Paper, RoundedCornerShape(12.dp))
            .border(1.dp, GrayLight, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DurationChip(min: Int, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Ink else Paper, RoundedCornerShape(14.dp))
            .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(fmtMinutes(min), color = if (on) Paper else Gray, fontSize = 15.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun fmtMinutes(min: Int): String {
    val h = min / 60; val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}시간 ${m}분"
        h > 0 -> "${h}시간"
        else -> "${m}분"
    }
}

// ============================ 집중 잠금 진행 (카운트다운) ============================

@Composable
fun FocusLockScreen(prefs: Prefs, onExpire: () -> Unit, onUnlock: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (prefs.focusLockUntil - nowMs <= 0) { onExpire(); break }
            delay(500)
        }
    }

    // 잠금 중 뒤로가기 무시
    BackHandler(enabled = true) { /* 차단 */ }

    val remainMs = (prefs.focusLockUntil - nowMs).coerceAtLeast(0)

    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        if (!showPin) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("집중 잠금 중", color = Gray, fontSize = 15.sp)
                Spacer(Modifier.height(24.dp))
                Text(fmtRemain(remainMs), color = Paper, fontSize = 72.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                Spacer(Modifier.height(16.dp))
                Text("남음", color = Gray, fontSize = 16.sp)
                Spacer(Modifier.height(72.dp))
                Text("복구코드로 해제",
                    color = Gray, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { showPin = true }
                        .border(1.dp, Gray, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp))
            }
        } else {
            FocusPinUnlock(
                verify = prefs::verifyPin,
                onSuccess = onUnlock,
                onCancel = { showPin = false }
            )
        }
    }
}

private fun fmtRemain(ms: Long): String {
    val total = (ms / 1000).toInt()
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

// ============================ 임시(3분) 해제 안내 ============================

@Composable
fun TempUnlockScreen(prefs: Prefs, onRelockNow: () -> Unit, onContinue: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (prefs.tempUnlockUntil - nowMs <= 0) { onRelockNow(); break }
            delay(500)
        }
    }
    BackHandler(enabled = true) { onContinue() }
    val remainMs = (prefs.tempUnlockUntil - nowMs).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().background(Paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("임시 해제 중", color = Gray, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        Text(fmtRemain(remainMs), color = Ink, fontSize = 68.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
        Spacer(Modifier.height(8.dp))
        Text("뒤 자동으로 다시 잠겨요", color = Gray, fontSize = 14.sp)
        Spacer(Modifier.height(56.dp))

        // 계속 쓰기 (홈으로)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink, RoundedCornerShape(14.dp))
                .clickable(onClick = onContinue).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) { Text("계속 쓰기", color = Paper, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.height(12.dp))
        // 지금 다시 잠그기
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, GrayLight, RoundedCornerShape(14.dp))
                .clickable(onClick = onRelockNow).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) { Text("지금 다시 잠그기", color = Ink, style = MaterialTheme.typography.labelLarge) }
    }
}

// 어두운 배경 위 복구코드(PIN) 입력
@Composable
private fun FocusPinUnlock(verify: (String) -> Boolean, onSuccess: () -> Unit, onCancel: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(entered) {
        if (entered.length == Prefs.PIN_LEN) {
            if (verify(entered)) onSuccess()
            else { error = true; delay(700); entered = ""; error = false }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("복구코드 입력", color = Paper, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(if (error) "코드가 일치하지 않습니다" else "영문·숫자 ${Prefs.PIN_LEN}자리",
            color = if (error) Paper else Gray, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))

        // 어두운 배경용: 칸을 직접 그린다 (흰 테두리)
        Box(contentAlignment = Alignment.Center) {
            DarkPinBoxes(entered, error)
            BasicTextField(
                value = entered,
                onValueChange = { entered = Prefs.normalizePinInput(it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = TextStyle(color = Color.Transparent),
                modifier = Modifier.matchParentSize().focusRequester(focus)
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("뒤로", color = Gray, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable(onClick = onCancel).padding(12.dp))
    }
}

@Composable
private fun DarkPinBoxes(text: String, error: Boolean) {
    val rows = (0 until Prefs.PIN_LEN).chunked(5)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val ch = text.getOrNull(i)
                    Box(
                        Modifier.background(Ink, RoundedCornerShape(12.dp))
                            .border(1.5.dp, if (error) Gray else Paper, RoundedCornerShape(12.dp))
                            .padding(0.dp)
                            .height(56.dp).width(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ch?.toString() ?: "", color = Paper, fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
