package com.studylock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylock.Prefs
import kotlinx.coroutines.delay

/** 잠금 게이트: 화면 켜질 때 PIN/패턴 요구. 복구코드로도 풀림. */
@Composable
fun ScreenLockGate(prefs: Prefs, onUnlocked: () -> Unit) {
    BackHandler(enabled = true) { /* 빠져나갈 수 없음 */ }
    var useRecovery by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        if (!useRecovery) { LockClock(); Spacer(Modifier.height(28.dp)) }
        else Spacer(Modifier.height(24.dp))
        if (useRecovery) {
            RecoveryUnlock(prefs::verifyPin, onUnlocked, onBack = { useRecovery = false })
        } else {
            when (prefs.screenLockType) {
                2 -> {
                    Text("패턴을 그리세요", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Spacer(Modifier.height(40.dp))
                    PatternPad { drawn -> if (prefs.verifyScreenLock(drawn)) onUnlocked() }
                }
                else -> {
                    Text("PIN 입력", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Spacer(Modifier.height(40.dp))
                    NumPinGate(verify = prefs::verifyScreenLock, onSuccess = onUnlocked)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("복구코드로 잠금 해제", color = Gray, fontSize = 14.sp,
                modifier = Modifier.clickable { useRecovery = true }.padding(12.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LockClock() {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = java.time.LocalTime.now(); delay(10_000) } }
    val date = java.time.LocalDate.now()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%02d:%02d".format(now.hour, now.minute),
            color = Ink, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
        Spacer(Modifier.height(4.dp))
        Text("%d월 %d일".format(date.monthValue, date.dayOfMonth), color = Gray, fontSize = 15.sp)
    }
}

// ---------------- 숫자 PIN ----------------

@Composable
fun NumPinGate(verify: (String) -> Boolean, onSuccess: () -> Unit, minLen: Int = 4) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val shown = entered.length.coerceAtLeast(minLen)
            repeat(shown) { i ->
                Box(Modifier.size(16.dp)
                    .background(if (i < entered.length) Ink else Paper, CircleShape)
                    .border(1.5.dp, if (error) Ink else GrayLight, CircleShape))
            }
        }
        Spacer(Modifier.height(36.dp))
        NumKeypad(
            onDigit = { if (entered.length < 12) { entered += it; error = false } },
            onDelete = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
            onOk = {
                if (verify(entered)) onSuccess() else { error = true; entered = "" }
            }
        )
    }
}

@Composable
fun NumKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit, onOk: () -> Unit) {
    val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("⌫","0","✓"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    Box(Modifier.size(74.dp).clickable {
                        when (key) { "⌫" -> onDelete(); "✓" -> onOk(); else -> onDigit(key) }
                    }, contentAlignment = Alignment.Center) {
                        Text(key, fontSize = if (key.length == 1 && key[0].isDigit()) 28.sp else 24.sp,
                            fontWeight = FontWeight.Medium, color = Ink)
                    }
                }
            }
        }
    }
}

// ---------------- 패턴 ----------------

/** 3x3 패턴 입력. 손 떼면 onComplete(시퀀스 "0-1-2..") */
@Composable
fun PatternPad(error: Boolean = false, onComplete: (String) -> Unit) {
    var path by remember { mutableStateOf(listOf<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }   // 현재 손가락 위치(라이브 라인용)
    val side = 280.dp

    Box(Modifier.size(side).pointerInput(Unit) {
        val w = size.width.toFloat(); val h = size.height.toFloat()
        fun centerOf(i: Int) = Offset((i % 3 + 0.5f) / 3f * w, (i / 3 + 0.5f) / 3f * h)
        val hit = (w / 3f) * 0.32f
        fun tryHit(p: Offset) {
            for (i in 0 until 9) {
                if (i !in path) {
                    val c = centerOf(i)
                    val dx = p.x - c.x; val dy = p.y - c.y
                    if (dx * dx + dy * dy < hit * hit) { path = path + i; break }
                }
            }
        }
        detectDragGestures(
            onDragStart = { p -> path = emptyList(); finger = p; tryHit(p) },
            onDragEnd = {
                finger = null
                if (path.size >= 4) onComplete(path.joinToString("-"))
                path = emptyList()
            },
            onDragCancel = { finger = null; path = emptyList() },
            onDrag = { change, _ ->
                finger = change.position
                tryHit(change.position)
            }
        )
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            fun c(i: Int) = Offset((i % 3 + 0.5f) / 3f * w, (i / 3 + 0.5f) / 3f * h)
            // 연결선
            for (k in 0 until path.size - 1) {
                drawLine(Ink, c(path[k]), c(path[k + 1]), strokeWidth = 8f)
            }
            // 마지막 점 → 손가락까지 라이브 라인 (실제 폰 잠금처럼)
            val f = finger
            if (f != null && path.isNotEmpty()) {
                drawLine(Ink, c(path.last()), f, strokeWidth = 8f)
            }
            // 점
            for (i in 0 until 9) {
                val on = i in path
                drawCircle(if (on) Ink else GrayLight, radius = if (on) 16f else 12f, center = c(i))
            }
        }
    }
}

// ---------------- 복구코드 ----------------

@Composable
private fun RecoveryUnlock(verify: (String) -> Boolean, onUnlocked: () -> Unit, onBack: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(entered) {
        if (entered.length == Prefs.PIN_LEN) {
            if (verify(entered)) onUnlocked() else { error = true; delay(700); entered = ""; error = false }
        }
    }
    Text("복구코드 입력", style = MaterialTheme.typography.titleLarge, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text(if (error) "코드가 일치하지 않습니다" else "영문·숫자 ${Prefs.PIN_LEN}자리",
        style = MaterialTheme.typography.bodyMedium, color = if (error) Ink else Gray)
    Spacer(Modifier.height(28.dp))
    Box(contentAlignment = Alignment.Center) {
        PinBoxes(text = entered, error = error)
        BasicTextField(value = entered, onValueChange = { entered = Prefs.normalizePinInput(it) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(Color.Transparent), textStyle = TextStyle(color = Color.Transparent),
            modifier = Modifier.matchParentSize().focusRequester(focus))
    }
    Spacer(Modifier.height(40.dp))
    Text("← PIN/패턴으로 돌아가기", color = Gray, fontSize = 15.sp,
        modifier = Modifier.clickable(onClick = onBack).padding(12.dp))
}
