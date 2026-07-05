package com.studylock.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.studylock.AppCatalog
import com.studylock.AppEntry
import com.studylock.LockManager
import com.studylock.Prefs
import kotlinx.coroutines.delay

private enum class PanelState { PIN, MENU, ADD_APPS, CONFIRM_RELEASE }

@Composable
fun PinPanel(
    prefs: Prefs,
    lockManager: LockManager,
    onClose: () -> Unit,
    onAppsChanged: () -> Unit,
    onTempUnlock: () -> Unit,
    onReleased: () -> Unit
) {
    var state by remember { mutableStateOf(PanelState.PIN) }

    when (state) {
        PanelState.PIN -> PinGate(
            verify = prefs::verifyPin,
            onClose = onClose,
            onSuccess = { state = PanelState.MENU }
        )
        PanelState.MENU -> PanelMenu(
            onAddApps = { state = PanelState.ADD_APPS },
            onTempUnlock = onTempUnlock,
            onRelease = { state = PanelState.CONFIRM_RELEASE },
            onClose = onClose
        )
        PanelState.ADD_APPS -> AddAppsScreen(
            prefs = prefs,
            onBack = { state = PanelState.MENU },
            onSave = { newSet ->
                prefs.allowedPackages = newSet
                lockManager.refreshAllowedApps(prefs)
                onAppsChanged()
                state = PanelState.MENU
            }
        )
        PanelState.CONFIRM_RELEASE -> ConfirmReleaseScreen(
            onBack = { state = PanelState.MENU },
            onConfirm = onReleased   // lockTask 종료 → 정책 원복 순서는 호출 측에서 처리
        )
    }
}

// ---------- PIN 입력 ----------

@Composable
private fun PinGate(verify: (String) -> Boolean, onClose: () -> Unit, onSuccess: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var fails by remember { mutableIntStateOf(0) }
    var cooldown by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    // 5회 실패부터 잠금: 30s, 60s, 120s … (지수 백오프, 상한 600s)
    LaunchedEffect(cooldown) {
        if (cooldown > 0) { delay(1000); cooldown -= 1 }
    }

    LaunchedEffect(entered) {
        if (entered.length == Prefs.PIN_LEN && cooldown == 0) {
            if (verify(entered)) {
                onSuccess()
            } else {
                fails += 1
                error = true
                delay(700)
                entered = ""
                error = false
                if (fails >= 5) {
                    // 지수 백오프. shift 상한을 둬 Int 오버플로(=음수로 잠금 무력화) 방지
                    cooldown = (30 * (1 shl (fails - 5).coerceAtMost(20))).coerceAtMost(600)
                }
            }
        }
    }

    val blocked = cooldown > 0
    // 진입 시(및 쿨다운 해제 시) 자동 포커스 → 키보드 표시
    LaunchedEffect(blocked) { if (!blocked) runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().background(Paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Text("PIN 입력", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                blocked -> "시도 초과 — ${cooldown}초 후 다시"
                error -> "PIN 이 일치하지 않습니다"
                else -> "영문·숫자 ${Prefs.PIN_LEN}자리 입력"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (error || blocked) Ink else Gray
        )
        Spacer(Modifier.height(40.dp))

        // 균일 박스 위에 투명 입력 필드를 겹쳐, 박스를 탭하면 키보드가 뜬다.
        Box(contentAlignment = Alignment.Center) {
            PinBoxes(text = entered, error = error)
            BasicTextField(
                value = entered,
                onValueChange = { if (!blocked) entered = Prefs.normalizePinInput(it) },
                enabled = !blocked,
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

        Spacer(Modifier.weight(1f))
        Text(
            "닫기",
            style = MaterialTheme.typography.bodyLarge, color = Gray,
            modifier = Modifier.clickable(onClick = onClose).padding(12.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- 메뉴 ----------

@Composable
private fun PanelMenu(onAddApps: () -> Unit, onTempUnlock: () -> Unit, onRelease: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Paper).padding(28.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Text("관리", style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp), color = Ink)
        Spacer(Modifier.height(32.dp))
        MenuCard("허용 앱 추가/변경", "잠금 기간에 열 수 있는 앱을 조정합니다", onAddApps)
        Spacer(Modifier.height(14.dp))
        MenuCard("3분 해제", "3분 동안 잠금을 풀고 폰을 자유롭게 씁니다. 끝나면 자동 재잠금", onTempUnlock)
        Spacer(Modifier.height(14.dp))
        MenuCard("완전 해제", "잠금을 끝내고 기기를 정상 상태로 되돌립니다", onRelease)
        Spacer(Modifier.weight(1f))
        Text(
            "닫기",
            style = MaterialTheme.typography.bodyLarge, color = Gray,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(14.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuCard(title: String, desc: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GrayLight, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- 앱 추가 ----------

@Composable
private fun AddAppsScreen(prefs: Prefs, onBack: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        prefs.allowedPackages.forEach { selected[it] = true }
        apps = AppCatalog.launchableApps(context)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(56.dp))
        Text("허용 앱", style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp), color = Ink)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("불러오는 중…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn {
                    items(apps, key = { it.packageName }) { e ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selected[e.packageName] = !(selected[e.packageName] ?: false) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bmp = remember(e.packageName) { e.icon.toBitmap(72, 72).asImageBitmap() }
                            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(e.label, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = selected[e.packageName] ?: false,
                                onCheckedChange = { selected[e.packageName] = it },
                                colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = GrayLight, checkmarkColor = Paper)
                            )
                        }
                    }
                }
            }
        }
        PrimaryButton("저장") { onSave(selected.filter { it.value }.keys.toSet()) }
        Spacer(Modifier.height(4.dp))
        Text(
            "뒤로", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(14.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- 완전 해제 확인 ----------

@Composable
private fun ConfirmReleaseScreen(onBack: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).padding(28.dp)) {
        Spacer(Modifier.height(72.dp))
        Text("완전 해제", style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp), color = Ink)
        Spacer(Modifier.height(16.dp))
        Text(
            "잠금이 즉시 종료되고 기기 관리자 권한이 반납됩니다.\n목표일 전이라도 모든 앱이 다시 열립니다.\n계속하려면 아래를 미세요.",
            style = MaterialTheme.typography.bodyLarge, color = Gray
        )
        Spacer(Modifier.weight(1f))
        SlideToConfirm(text = "밀어서 완전 해제", onConfirm = onConfirm)
        Spacer(Modifier.height(8.dp))
        Text(
            "뒤로", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(14.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
    }
}
