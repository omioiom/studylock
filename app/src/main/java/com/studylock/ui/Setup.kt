package com.studylock.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.studylock.AppCatalog
import com.studylock.AppEntry
import com.studylock.AppRemover
import com.studylock.DateUtil
import com.studylock.Prefs
import java.time.Instant
import java.time.ZoneOffset

data class SetupResult(
    val targetMillis: Long,
    val ddayLabel: String,
    val allowed: Set<String>,
    val blockTime: Boolean,
    val blockSafeBoot: Boolean,
    val blockStatusBar: Boolean,
    val pin: String
)

@Composable
fun SetupFlow(
    prefs: Prefs,
    onQuit: () -> Unit,
    onActivate: (SetupResult) -> Unit
) {
    var step by remember { mutableStateOf(0) }

    // 수집 상태
    var targetMillis by remember { mutableStateOf(DateUtil.toEpochMillis(DateUtil.defaultTarget)) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var blockTime by remember { mutableStateOf(true) }
    var blockSafeBoot by remember { mutableStateOf(true) }
    var blockStatusBar by remember { mutableStateOf(true) }
    val pin = remember { Prefs.generatePin() }
    var ddayLabel by remember { mutableStateOf("수능") }

    when (step) {
        0 -> IntroStep(onQuit = onQuit) { step = 1 }
        1 -> DateStep(targetMillis, ddayLabel, onBack = { step = 0 }) { m, name ->
            targetMillis = m; ddayLabel = name; step = 2
        }
        2 -> AppsStep(selected, onBack = { step = 1 }) { step = 3 }
        3 -> CleanupStep(
            keepPackages = selected.filter { it.value }.keys,
            onBack = { step = 2 },
            onNext = { step = 4 }
        )
        4 -> OptionsStep(
            blockTime, blockSafeBoot, blockStatusBar,
            onTime = { blockTime = it }, onSafe = { blockSafeBoot = it }, onBar = { blockStatusBar = it },
            onBack = { step = 3 }, onNext = { step = 5 }
        )
        5 -> ReviewStep(
            targetMillis = targetMillis,
            allowedCount = selected.count { it.value },
            blockTime = blockTime, blockSafeBoot = blockSafeBoot, blockStatusBar = blockStatusBar,
            onBack = { step = 4 }, onConfirm = { step = 6 }
        )
        6 -> PinStep(pin) { step = 7 }
        7 -> ActivateStep(onQuit = onQuit) {
            val allowed = selected.filter { it.value }.keys.toSet()
            onActivate(SetupResult(targetMillis, ddayLabel, allowed, blockTime, blockSafeBoot, blockStatusBar, pin))
        }
    }
}

// ---------- 공통 스캐폴드 ----------

@Composable
private fun StepScaffold(
    title: String,
    subtitle: String? = null,
    primaryLabel: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(64.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp), color = Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(10.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(28.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (footer != null) { footer(); Spacer(Modifier.height(12.dp)) }
        if (primaryLabel.isNotBlank()) {
            PrimaryButton(primaryLabel, enabled = primaryEnabled, onClick = onPrimary)
        }
        if (backLabel != null && onBack != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                backLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack)
                    .padding(vertical = 14.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Ink else GrayLight, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Paper, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * PIN 표시/입력 칸. 모든 칸이 동일한 고정 크기(균일성 유지).
 * 10칸을 5×2 로 배치, 모노스페이스로 글자 폭까지 통일.
 * @param text 현재 채워진 문자열. 빈 칸은 공백으로 표시(마스킹 X — 적어두기 편하게).
 */
@Composable
fun PinBoxes(text: String, slots: Int = Prefs.PIN_LEN, error: Boolean = false) {
    val perRow = 5
    val rows = (0 until slots).chunked(perRow)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val ch = text.getOrNull(i)
                    Box(
                        Modifier
                            .size(52.dp, 62.dp)
                            .border(1.5.dp, if (error) Gray else Ink, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ch?.toString() ?: "",
                            fontSize = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                    }
                }
            }
        }
    }
}

// ---------- 0. 인트로 ----------

@Composable
private fun IntroStep(onQuit: () -> Unit, onNext: () -> Unit) {
    StepScaffold(
        title = "StudyLock",
        subtitle = "설정을 마치면 목표일까지 허용한 앱만 열립니다.\n해제하려면 1회 발급되는 PIN 이 필요합니다.",
        primaryLabel = "설정 시작",
        onPrimary = onNext,
        backLabel = "그만두기 · 앱 삭제",   // 시작 전엔 삭제 자유
        onBack = onQuit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Bullet("1", "목표일(수능) 설정")
            Bullet("2", "허용할 앱 선택")
            Bullet("3", "불필요한 앱 정리 (선택)")
            Bullet("4", "잠금 옵션 선택")
            Bullet("5", "PIN 확인 후 슬라이드로 시작")
        }
    }
}

@Composable
private fun Bullet(n: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(Ink, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(n, color = Paper, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

// ---------- 1. 날짜 ----------

@OptIn(ExperimentalMaterial3Api::class)
private object FutureDates : androidx.compose.material3.SelectableDates {
    // 오늘(UTC 자정) 이후만 선택 가능 → 과거 선택 시 즉시 자동해제 방지
    private val todayUtc = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
    override fun isSelectableYear(year: Int) = year >= java.time.LocalDate.now().year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(current: Long, currentLabel: String, onBack: () -> Unit, onNext: (Long, String) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DateUtil.toUtcMillis(current),
        selectableDates = FutureDates
    )
    var label by remember { mutableStateOf(currentLabel) }
    val picked = state.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
    }
    StepScaffold(
        title = "목표일",
        subtitle = "목표 이름과 날짜를 정하세요. D-day 로 표시됩니다.",
        primaryLabel = "다음",
        primaryEnabled = picked != null && label.isNotBlank(),
        onPrimary = { picked?.let { onNext(DateUtil.toEpochMillis(it), label.trim().ifBlank { "수능" }) } },
        backLabel = "뒤로", onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = label,
                onValueChange = { if (it.length <= 8) label = it },
                singleLine = true,
                label = { Text("목표 이름 (예: 수능, 중간고사)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            DatePicker(state = state, title = null, headline = null, showModeToggle = false)
        }
    }
}

// ---------- 2. 허용 앱 ----------

@Composable
private fun AppsStep(
    selected: SnapshotStateMap<String, Boolean>,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val list = AppCatalog.launchableApps(context)
        // 추천 앱 기본 체크
        list.forEach { e ->
            if (!selected.containsKey(e.packageName) && isRecommended(e)) {
                selected[e.packageName] = true
            }
        }
        apps = list
        loading = false
    }

    val count = selected.count { it.value }
    StepScaffold(
        title = "허용 앱",
        subtitle = "체크한 앱만 잠금 기간에 열립니다. 나머지는 전부 차단됩니다.",
        primaryLabel = if (count == 0) "1개 이상 선택" else "다음 ($count)",
        primaryEnabled = count > 0,
        onPrimary = onNext,
        backLabel = "뒤로", onBack = onBack
    ) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("앱 목록 불러오는 중…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn {
                items(apps, key = { it.packageName }) { e ->
                    AppRow(
                        entry = e,
                        checked = selected[e.packageName] == true,
                        onToggle = { selected[e.packageName] = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(entry: AppEntry, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = remember(entry.packageName) { entry.icon.toBitmap(72, 72).asImageBitmap() }
        androidx.compose.foundation.Image(
            bitmap = bmp, contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(entry.label, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
        Checkbox(
            checked = checked, onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = GrayLight, checkmarkColor = Paper)
        )
    }
}

// ---------- 2.5 불필요한 앱 삭제 (선택) ----------

@Composable
private fun CleanupStep(
    keepPackages: Set<String>,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var confirming by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = AppRemover.uninstallableApps(context).filter { it.packageName !in keepPackages }
        loading = false
    }

    val count = selected.count { it.value }

    if (confirming) {
        StepScaffold(
            title = "앱 삭제",
            subtitle = "선택한 ${count}개 앱을 기기에서 완전히 삭제합니다.\n되돌리려면 다시 설치해야 합니다.",
            primaryLabel = if (working) "삭제 중…" else "삭제하고 계속",
            primaryEnabled = !working,
            onPrimary = {
                if (!working) {
                    working = true
                    AppRemover.uninstallAll(context, selected.filter { it.value }.keys)
                    onNext()
                }
            },
            backLabel = "뒤로", onBack = { if (!working) confirming = false }
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(apps.filter { selected[it.packageName] == true }, key = { it.packageName }) { e ->
                    Text("· ${e.label}", style = MaterialTheme.typography.bodyLarge, color = Ink)
                }
            }
        }
        return
    }

    StepScaffold(
        title = "불필요한 앱 정리",
        subtitle = "공부에 방해되는 앱을 골라 삭제하세요. (건너뛰어도 됩니다)\n허용 앱으로 고른 앱은 목록에 없습니다.",
        primaryLabel = if (count == 0) "건너뛰기" else "선택 삭제 ($count)",
        onPrimary = { if (count == 0) onNext() else confirming = true },
        backLabel = "뒤로", onBack = onBack
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("앱 목록 불러오는 중…", style = MaterialTheme.typography.bodyMedium)
            }
            apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("삭제할 수 있는 사용자 앱이 없습니다.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn {
                items(apps, key = { it.packageName }) { e ->
                    AppRow(
                        entry = e,
                        checked = selected[e.packageName] == true,
                        onToggle = { selected[e.packageName] = it }
                    )
                }
            }
        }
    }
}

// ---------- 3. 옵션 ----------

@Composable
private fun OptionsStep(
    blockTime: Boolean, blockSafeBoot: Boolean, blockStatusBar: Boolean,
    onTime: (Boolean) -> Unit, onSafe: (Boolean) -> Unit, onBar: (Boolean) -> Unit,
    onBack: () -> Unit, onNext: () -> Unit
) {
    StepScaffold(
        title = "잠금 옵션",
        subtitle = "기본값 권장. 우회 경로를 막습니다.",
        primaryLabel = "다음",
        onPrimary = onNext,
        backLabel = "뒤로", onBack = onBack
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionRow("시계 조작 차단", "날짜를 앞당겨 푸는 우회 방지", blockTime, onTime)
            OptionRow("안전모드 차단", "안전모드로 우회 방지", blockSafeBoot, onSafe)
            OptionRow("알림창 차단", "상태바 당겨 설정 진입 방지", blockStatusBar, onBar)
        }
    }
}

@Composable
private fun OptionRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, GrayLight, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Paper, checkedTrackColor = Ink,
                uncheckedThumbColor = Gray, uncheckedTrackColor = Paper, uncheckedBorderColor = GrayLight
            )
        )
    }
}

// ---------- 4. 확인 ----------

@Composable
private fun ReviewStep(
    targetMillis: Long, allowedCount: Int,
    blockTime: Boolean, blockSafeBoot: Boolean, blockStatusBar: Boolean,
    onBack: () -> Unit, onConfirm: () -> Unit
) {
    StepScaffold(
        title = "확인",
        subtitle = "아래 설정으로 잠금을 시작합니다.",
        primaryLabel = "이대로 시작",
        onPrimary = onConfirm,
        backLabel = "뒤로", onBack = onBack
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SummaryRow("목표일", DateUtil.format(targetMillis))
            SummaryRow("D-day", "D-${DateUtil.daysUntil(targetMillis)}")
            SummaryRow("허용 앱", "${allowedCount}개")
            SummaryRow("시계 조작 차단", if (blockTime) "켜짐" else "꺼짐")
            SummaryRow("안전모드 차단", if (blockSafeBoot) "켜짐" else "꺼짐")
            SummaryRow("알림창 차단", if (blockStatusBar) "켜짐" else "꺼짐")
        }
    }
}

@Composable
private fun SummaryRow(k: String, v: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(k, style = MaterialTheme.typography.bodyLarge, color = Gray)
        Text(v, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

// ---------- 5. PIN ----------

@Composable
private fun PinStep(pin: String, onNext: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    StepScaffold(
        title = "PIN",
        subtitle = "이 PIN 은 지금 한 번만 표시됩니다.\n앱 추가·완전 해제에 필요합니다. 종이에 적어 보관하세요.",
        primaryLabel = "다음",
        primaryEnabled = saved,
        onPrimary = onNext
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            PinBoxes(text = pin)
            Spacer(Modifier.height(40.dp))
            Row(
                Modifier.clickable { saved = !saved }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(26.dp)
                        .background(if (saved) Ink else Paper, RoundedCornerShape(6.dp))
                        .border(1.5.dp, Ink, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) { if (saved) Icon(Icons.Filled.Check, null, tint = Paper, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Text("PIN 을 적어두었습니다", style = MaterialTheme.typography.bodyLarge, color = Ink)
            }
        }
    }
}

// ---------- 6. 슬라이드 확정 ----------

@Composable
private fun ActivateStep(onQuit: () -> Unit, onActivate: () -> Unit) {
    StepScaffold(
        title = "잠금 시작",
        subtitle = "밀면 즉시 잠금이 활성화됩니다.\n이후엔 PIN 또는 공장초기화로만 해제됩니다.",
        primaryLabel = "",
        primaryEnabled = false,
        onPrimary = {},
        backLabel = "그만두기 · 앱 삭제",   // 슬라이드 시작 전엔 삭제 자유
        onBack = onQuit
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SlideToConfirm(text = "밀어서 잠금 시작", onConfirm = onActivate)
        }
    }
}

// ActivateStep 은 primary 버튼 숨김이 필요 → 별도 스캐폴드 없이 단순화
private fun isRecommended(e: AppEntry): Boolean {
    val k = (e.packageName + " " + e.label).lowercase()
    val keys = listOf(
        "dialer", "phone", "전화",
        "messag", "메시지", "문자",
        "kakao", "카카오", "카톡",
        "clock", "시계", "알람",
        "calcul", "계산",
        "map", "지도", "navermap", "kakaomap",
        "bank", "은행", "뱅크", "뱅킹"
    )
    return keys.any { k.contains(it) }
}
