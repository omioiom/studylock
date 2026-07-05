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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.studylock.AppLimit
import com.studylock.BlockWindow
import com.studylock.LockManager
import com.studylock.Prefs
import com.studylock.ScreenTime
import com.studylock.ScreenTimeReceiver
import com.studylock.ScreenTimeRules
import com.studylock.TimeBlock
import com.studylock.TimetableLoader

private enum class StSub { MAIN, ADD_LIMIT, ADD_WINDOW, ADD_SCHEDULE, EDIT_TOTAL, STATS }

private fun fmtMin(m: Int): String {
    val h = m / 60; val mm = m % 60
    return when { h > 0 && mm > 0 -> "${h}시간 ${mm}분"; h > 0 -> "${h}시간"; else -> "${mm}분" }
}
private fun hhmm(min: Int): String = "%02d:%02d".format((min / 60) % 24, min % 60)

private fun appLabel(prefs: Prefs, context: android.content.Context, pkg: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)).toString()
    }.getOrDefault(pkg)

@Composable
fun ScreenTimeRoot(prefs: Prefs, lock: LockManager, onClose: () -> Unit, initialLimitPkg: String? = null) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(ScreenTime.parseRules(prefs.screenTimeJson)) }
    // 앱 롱프레스로 진입했으면 바로 '앱별 하루 제한 추가'로
    var sub by remember { mutableStateOf(if (initialLimitPkg != null) StSub.ADD_LIMIT else StSub.MAIN) }
    var limitPreselect by remember { mutableStateOf(initialLimitPkg) }
    // 잠긴 규칙 수정 시 PIN 요구: 통과 후 실행할 동작
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun persist(r: ScreenTimeRules) {
        rules = r
        prefs.screenTimeJson = ScreenTime.serialize(r)
        val t = java.time.LocalTime.now()
        lock.applyScreenTime(prefs, System.currentTimeMillis(), t.hour * 60 + t.minute)
        ScreenTimeReceiver.ensure(context, prefs)
    }

    // 잠긴(1시간 경과) 규칙이면 PIN 게이트, 아니면 즉시 실행
    fun guarded(createdAt: Long, action: () -> Unit) {
        if (ScreenTime.editable(createdAt, System.currentTimeMillis())) action()
        else pending = action
    }

    if (pending != null) {
        StPinGate(
            verify = prefs::verifyPin,
            onSuccess = { val a = pending; pending = null; a?.invoke() },
            onCancel = { pending = null }
        )
        return
    }

    when (sub) {
        StSub.MAIN -> StMain(
            prefs = prefs, context = context, rules = rules,
            onClose = onClose,
            onAddLimit = { sub = StSub.ADD_LIMIT },
            onAddWindow = { sub = StSub.ADD_WINDOW },
            onAddSchedule = { sub = StSub.ADD_SCHEDULE },
            onEditTotal = { sub = StSub.EDIT_TOTAL },
            onStats = { sub = StSub.STATS },
            onDeleteLimit = { lim -> guarded(lim.createdAt) { persist(rules.copy(appLimits = rules.appLimits - lim)) } },
            onDeleteWindow = { w -> guarded(w.createdAt) { persist(rules.copy(windows = rules.windows - w)) } },
            onClearTotal = { guarded(rules.totalCreatedAt) { persist(rules.copy(totalLimitMin = 0, totalCreatedAt = 0)) } }
        )
        StSub.ADD_LIMIT -> AddLimitScreen(prefs, context, preselect = limitPreselect,
            onBack = { limitPreselect = null; sub = StSub.MAIN },
            onSave = { pkg, min, days ->
                val now = System.currentTimeMillis()
                val existing = rules.appLimits.firstOrNull { it.pkg == pkg }
                val doSave = {
                    val list = rules.appLimits.filter { it.pkg != pkg } + AppLimit(pkg, min, now, days)
                    // 무료: 앱별 제한 FREE_ST_RULES 개까지
                    if (prefs.isPremium || list.size <= FREE_ST_RULES) persist(rules.copy(appLimits = list))
                    limitPreselect = null; sub = StSub.MAIN
                }
                // 같은 앱 제한이 이미 있으면 교체 = 잠금 확인(잠긴 걸 느슨하게 못 바꾸게)
                if (existing != null) guarded(existing.createdAt, doSave) else doSave()
            })
        StSub.ADD_WINDOW -> AddWindowScreen(prefs, context,
            onBack = { sub = StSub.MAIN },
            onSave = { apps, s, e, days ->
                val now = System.currentTimeMillis()
                // 같은 시간대·같은 요일의 수동 차단창이 이미 있으면 그 컨테이너에 앱만 합침(새 창 X)
                val existing = rules.windows.firstOrNull { it.blockId.isBlank() && it.startMin == s && it.endMin == e && it.days == days }
                if (existing != null) {
                    val merged = existing.copy(apps = (existing.apps + apps).distinct())
                    persist(rules.copy(windows = rules.windows.map { if (it === existing) merged else it }))
                } else if (prefs.isPremium || rules.windows.size < FREE_ST_RULES) {
                    persist(rules.copy(windows = rules.windows + BlockWindow(now, apps, s, e, now, days = days)))
                }
                sub = StSub.MAIN
            })
        StSub.ADD_SCHEDULE -> AddScheduleScreen(prefs, context,
            onBack = { sub = StSub.MAIN },
            onSave = { apps, picked, days ->
                val now = System.currentTimeMillis()
                var wins = rules.windows
                var added = 0
                picked.forEach { b ->
                    val e = b.endMin % 1440
                    // 같은 일정(blockId)·같은 요일 컨테이너가 있으면 앱만 합침
                    val existing = wins.firstOrNull {
                        it.days == days && (if (b.id.isNotBlank()) it.blockId == b.id
                        else it.blockId.isBlank() && it.startMin == b.startMin && it.endMin == e)
                    }
                    if (existing != null) {
                        val merged = existing.copy(apps = (existing.apps + apps).distinct())
                        wins = wins.map { if (it === existing) merged else it }
                    } else if (prefs.isPremium || wins.size < FREE_ST_RULES) {
                        // 새 컨테이너만 무료 슬롯 소모
                        wins = wins + BlockWindow(now + added, apps, b.startMin, e, now, b.content, b.id, days)
                        added++
                    }
                }
                if (wins !== rules.windows) persist(rules.copy(windows = wins))
                sub = StSub.MAIN
            })
        StSub.STATS -> StatsScreen(prefs, context, onBack = { sub = StSub.MAIN })
        StSub.EDIT_TOTAL -> EditTotalScreen(prefs, rules.totalLimitMin, rules.totalDays,
            onBack = { sub = StSub.MAIN },
            onSave = { min, days ->
                val now = System.currentTimeMillis()
                val doSave = { persist(rules.copy(totalLimitMin = min, totalDays = days, totalCreatedAt = if (min > 0) now else 0)); sub = StSub.MAIN }
                // 이미 전체 제한이 걸려 있으면 수정 = 잠금 확인
                if (rules.totalLimitMin > 0) guarded(rules.totalCreatedAt, doSave) else doSave()
            })
    }
}

// ---------------- 메인 ----------------

@Composable
private fun StMain(
    prefs: Prefs, context: android.content.Context, rules: ScreenTimeRules,
    onClose: () -> Unit, onAddLimit: () -> Unit, onAddWindow: () -> Unit, onAddSchedule: () -> Unit,
    onEditTotal: () -> Unit, onStats: () -> Unit,
    onDeleteLimit: (AppLimit) -> Unit, onDeleteWindow: (BlockWindow) -> Unit, onClearTotal: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    val now = System.currentTimeMillis()
    // 일정별 차단 이름 자동반영: 시간표에서 같은 시간대 블록의 현재 content 를 조회
    val ttBlocks = remember {
        TimetableLoader.parse(TimetableLoader.activeJson(context, prefs)).getOrNull()?.blocks ?: emptyList()
    }
    fun windowName(w: BlockWindow): String {
        if (w.label.isBlank()) return ""
        // 1) blockId 로 연결 → 이름·시간 둘 다 바껴도 현재 이름 추적
        if (w.blockId.isNotBlank()) ttBlocks.firstOrNull { it.id == w.blockId }?.let { return it.content }
        // 2) 시간 매칭(이름만 바뀐 경우)
        return ttBlocks.firstOrNull { it.startMin == w.startMin && it.endMin % 1440 == w.endMin }?.content ?: w.label
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("스크린타임", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
            Spacer(Modifier.weight(1f))
            Text("닫기", color = Gray, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("규칙은 만든 뒤 1시간까진 자유 수정, 그 후엔 복구코드로만 풀려요.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        // 통계 보기
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink, RoundedCornerShape(14.dp))
            .clickable(onClick = onStats).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text("사용시간 통계 보기", color = Paper, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(24.dp))

        // 전체 제한
        SectionTitle("전체 하루 제한")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, GrayLight, RoundedCornerShape(14.dp))
            .clickable(onClick = onEditTotal).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (rules.totalLimitMin > 0) {
                        Text(fmtMin(rules.totalLimitMin), color = Ink, style = MaterialTheme.typography.titleMedium)
                        Text("오늘 ${(ScreenTime.totalUsedSeconds(prefs) / 60)}분 사용" +
                            lockNote(rules.totalCreatedAt, now), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("꺼짐", color = Gray, style = MaterialTheme.typography.titleMedium)
                        Text("탭하여 전 앱 합산 제한 설정", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (rules.totalLimitMin > 0)
                    Text("끄기", color = Gray, fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onClearTotal).padding(6.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("앱별 하루 제한")
        if (prefs.isPremium || rules.appLimits.size < FREE_ST_RULES) AddRow("+ 앱별 제한 추가", onAddLimit)
        else LockedAddRow("앱별 제한은 무료 ${FREE_ST_RULES}개까지")
        rules.appLimits.sortedByDescending { it.createdAt }.forEach { lim ->
            RuleRow(
                title = appLabel(prefs, context, lim.pkg),
                sub = "${ScreenTime.usedSeconds(prefs, lim.pkg) / 60} / ${lim.limitMin}분" +
                    (if (prefs.timetablePerDay) "  · ${ScreenTime.daysLabel(lim.days)}" else "") + lockNote(lim.createdAt, now),
                onDelete = { onDeleteLimit(lim) }
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("시간대 차단")
        if (prefs.isPremium || rules.windows.size < FREE_ST_RULES) {
            AddRow("+ 시간대 직접 추가", onAddWindow)
            AddRow("+ 일정에서 차단 추가", onAddSchedule)
        } else LockedAddRow("시간대 차단은 무료 ${FREE_ST_RULES}개까지")
        rules.windows.sortedByDescending { it.createdAt }.forEach { w ->
            val time = "${hhmm(w.startMin)}–${hhmm(w.endMin)}"
            val name = windowName(w)
            RuleRow(
                title = if (name.isNotBlank()) "$name · $time" else time,
                sub = w.apps.joinToString(", ") { appLabel(prefs, context, it) } +
                    (if (prefs.timetablePerDay) "  · ${ScreenTime.daysLabel(w.days)}" else "") + lockNote(w.createdAt, now),
                onDelete = { onDeleteWindow(w) }
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

private fun lockNote(createdAt: Long, now: Long): String =
    if (ScreenTime.editable(createdAt, now)) "  · 수정가능(1시간 내)" else ""

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = Ink, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun RuleRow(title: String, sub: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(GrayField)          // 테두리 대신 은은한 채움 → 많아져도 깔끔
        .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(sub, color = Gray, fontSize = 12.5.sp, lineHeight = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(CircleShape).clickable(onClick = onDelete).padding(9.dp),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "삭제", tint = Gray, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .border(1.dp, GrayLight, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Ink, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(8.dp))
}

/** 무료 한도 초과 시: 프리미엄 잠금 안내 (비활성) */
@Composable
private fun LockedAddRow(label: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = Gray, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Gray, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(2.dp))
        Text("프리미엄에서 무제한 (설정 → 프리미엄)", color = Gray, fontSize = 11.sp)
    }
}

// ---------------- 통계 ----------------

private const val FREE_ST_RULES = 3   // 무료: 앱별제한/시간대차단 각각 이 개수까지

private fun fmtDur(sec: Long): String {
    val m = (sec / 60).toInt(); val h = m / 60; val mm = m % 60
    return if (h > 0) "${h}시간 ${mm}분" else "${mm}분"
}

@Composable
private fun StatsScreen(prefs: Prefs, context: android.content.Context, onBack: () -> Unit) {
    BackHandler(enabled = true) { onBack() }
    val now = System.currentTimeMillis()
    val today = remember { ScreenTime.todayUsage(prefs, now) }
    val hist = remember { ScreenTime.history(prefs) }
    val allDays = remember { listOf(today) + hist }            // 최신순(0=오늘)
    val recent = remember { allDays.take(7) }
    val maxSec = remember { (recent.maxOfOrNull { it.totalSec } ?: 1L).coerceAtLeast(1L) }
    val weekTotal = recent.sumOf { it.totalSec }

    // 날짜 넘겨보기 (0=오늘, 1=어제 ...)
    var dayOffset by remember { mutableIntStateOf(0) }
    val sel = allDays.getOrNull(dayOffset) ?: today
    val selApps = sel.apps.entries.filter { it.key != context.packageName }
        .sortedByDescending { it.value }.take(8)
    val dayLabel = when (dayOffset) { 0 -> "오늘"; 1 -> "어제"; else -> sel.date.takeLast(5).replace("-", "/") }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("사용시간 통계", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
            Spacer(Modifier.weight(1f))
            Text("뒤로", color = Gray, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onBack).padding(6.dp))
        }

        Spacer(Modifier.height(20.dp))
        // 날짜 네비게이터 ‹ 날짜 ›
        val hasOlder = dayOffset < allDays.size - 1
        val hasNewer = dayOffset > 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = if (hasOlder) Ink else GrayLight, fontSize = 30.sp,
                modifier = Modifier.clickable(enabled = hasOlder) { dayOffset++ }.padding(horizontal = 10.dp))
            Text(dayLabel, color = Ink, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("›", color = if (hasNewer) Ink else GrayLight, fontSize = 30.sp,
                modifier = Modifier.clickable(enabled = hasNewer) { dayOffset-- }.padding(horizontal = 10.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(fmtDur(sel.totalSec), color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        if (selApps.isEmpty()) Text("기록 없음", style = MaterialTheme.typography.bodyMedium)
        selApps.forEach { (pkg, sec) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(appLabel(prefs, context, pkg), color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(fmtDur(sec), color = Gray, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
        Spacer(Modifier.height(20.dp))
        Text("최근 7일  ·  합계 ${fmtDur(weekTotal)}", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(12.dp))
        recent.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.date.takeLast(5).replace("-", "/"), color = Gray, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                // 막대
                Box(Modifier.weight(1f).height(18.dp)) {
                    Box(Modifier.fillMaxHeight()
                        .fillMaxWidth((d.totalSec.toFloat() / maxSec).coerceIn(0.02f, 1f))
                        .background(Ink, RoundedCornerShape(4.dp)))
                }
                Spacer(Modifier.width(10.dp))
                Text(fmtDur(d.totalSec), color = Ink, fontSize = 12.sp, modifier = Modifier.width(72.dp))
            }
        }

        if (hist.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
            Spacer(Modifier.height(20.dp))
            Text("날짜별 전체", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(10.dp))
            hist.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(d.date, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(fmtDur(d.totalSec), color = Gray, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ---------------- 앱별 제한 추가 ----------------

@Composable
private fun AddLimitScreen(prefs: Prefs, context: android.content.Context,
                           preselect: String? = null,
                           onBack: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    BackHandler(enabled = true) { onBack() }
    var pkg by remember { mutableStateOf(preselect) }
    var minutes by remember { mutableIntStateOf(30) }
    var selDays by remember { mutableStateOf(todayDaySet()) }
    val apps = remember { prefs.allowedPackages.sortedBy { appLabel(prefs, context, it).lowercase() } }

    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("앱별 하루 제한", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(16.dp))
        if (prefs.timetablePerDay) {
            Text("요일", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(8.dp)); DayPicker(selDays) { selDays = it }; Spacer(Modifier.height(16.dp))
        }
        Text("앱 선택", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            LazyColumn {
                items(apps) { p ->
                    val on = p == pkg
                    Row(Modifier.fillMaxWidth().clickable { pkg = p }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(appLabel(prefs, context, p), color = if (on) Ink else Gray,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Filled.Check, null, tint = Ink, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("하루 ${fmtMin(minutes)}", color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-30, -10, 10, 30).forEach { d ->
                StepChip(if (d > 0) "+$d" else "$d") { minutes = (minutes + d).coerceIn(10, 600) }
            }
        }
        Spacer(Modifier.height(16.dp))
        FilledButton("저장", enabled = pkg != null && selDays.isNotEmpty()) { pkg?.let { onSave(it, minutes, daysMask(selDays)) } }
        BackText(onBack)
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------- 시간대 차단 추가 ----------------

@Composable
private fun AddWindowScreen(prefs: Prefs, context: android.content.Context,
                            onBack: () -> Unit, onSave: (List<String>, Int, Int, Int) -> Unit) {
    BackHandler(enabled = true) { onBack() }
    val sel = remember { mutableStateMapOf<String, Boolean>() }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(12 * 60) }
    var selDays by remember { mutableStateOf(todayDaySet()) }
    val apps = remember { prefs.allowedPackages.sortedBy { appLabel(prefs, context, it).lowercase() } }
    val chosen = sel.filter { it.value }.keys.toList()

    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("시간대 차단", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("선택한 앱을 그 시간대엔 아예 못 켜게 합니다.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        if (prefs.timetablePerDay) {
            DayPicker(selDays) { selDays = it }; Spacer(Modifier.height(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeStepper("시작", start) { start = it }
            Spacer(Modifier.width(12.dp))
            TimeStepper("종료", end) { end = it }
        }
        Spacer(Modifier.height(16.dp))
        Text("차단할 앱", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            LazyColumn {
                items(apps) { p ->
                    Row(Modifier.fillMaxWidth().clickable { sel[p] = !(sel[p] ?: false) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(appLabel(prefs, context, p), color = Ink, modifier = Modifier.weight(1f))
                        Checkbox(checked = sel[p] ?: false, onCheckedChange = { sel[p] = it },
                            colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = GrayLight, checkmarkColor = Paper))
                    }
                }
            }
        }
        FilledButton("저장", enabled = chosen.isNotEmpty() && end > start && selDays.isNotEmpty()) { onSave(chosen, start, end, daysMask(selDays)) }
        BackText(onBack)
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------- 일정에서 차단 추가 ----------------

@Composable
private fun AddScheduleScreen(prefs: Prefs, context: android.content.Context,
                              onBack: () -> Unit, onSave: (List<String>, List<TimeBlock>, Int) -> Unit) {
    var selDays by remember { mutableStateOf(todayDaySet()) }
    val todayDow = remember { ScreenTime.dowOf(System.currentTimeMillis()) }
    // 블록 목록은 고른 요일 중 하나(오늘이 포함되면 오늘) 기준
    val repDow = if (todayDow in selDays) todayDow else (selDays.minOrNull() ?: todayDow)
    val blocks = remember(repDow) {
        TimetableLoader.parse(TimetableLoader.editTargetJson(context, prefs, repDow)).getOrNull()?.blocks ?: emptyList()
    }
    // 고른 요일들의 시간표 '시간대'가 모두 같은지 (제목 무관, 시작·종료만 비교)
    val daysConsistent = remember(selDays) {
        if (selDays.size < 2) true
        else selDays.map { d ->
            TimetableLoader.parse(TimetableLoader.editTargetJson(context, prefs, d)).getOrNull()
                ?.blocks?.map { it.startMin to it.endMin }?.toSet() ?: emptySet()
        }.distinct().size <= 1
    }
    val selBlocks = remember { mutableStateMapOf<Int, Boolean>() }
    val selApps = remember { mutableStateMapOf<String, Boolean>() }
    val apps = remember { prefs.allowedPackages.sortedBy { appLabel(prefs, context, it).lowercase() } }
    val chosenApps = selApps.filter { it.value }.keys.toList()
    val chosenBlocks = blocks.filterIndexed { i, _ -> selBlocks[i] == true }
    var step by remember { mutableIntStateOf(0) }   // 0=일정 고르기, 1=앱 고르기
    // 기준 요일이 바뀌면 블록이 달라지니 선택 초기화
    LaunchedEffect(repDow) { selBlocks.clear() }

    BackHandler(enabled = true) { if (step == 0) onBack() else step = 0 }

    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        // 단계 표시
        Text(if (step == 0) "1단계 · 일정 고르기" else "2단계 · 앱 고르기",
            color = Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(if (step == 0) "차단할 일정" else "차단할 앱",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            if (step == 0) "고른 일정 시간 동안 아래 앱들이 잠겨요."
            else "고른 일정(${chosenBlocks.size}개) 동안 막을 앱을 고르세요.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(14.dp))
        if (step == 0 && prefs.timetablePerDay) {
            DayPicker(selDays) { selDays = it }
            if (!daysConsistent) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "고른 요일들의 시간표 시간대가 서로 달라요. 일정별 차단은 ‘그 일정의 시간대’를 저장해서 매일 같은 시각에 적용되는데, 요일마다 시간대가 다르면 어긋나요. 같은 시간표인 요일끼리만 고르거나, 요일을 1개만 고르세요.",
                    color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.weight(1f)) {
            if (step == 0) {
                if (blocks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("시간표가 비어있어요. 먼저 시간표를 채우세요.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else LazyColumn {
                    itemsIndexed(blocks) { i, b ->
                        val on = selBlocks[i] == true
                        Row(Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) Ink else Paper, RoundedCornerShape(12.dp))
                            .clickable { selBlocks[i] = !on }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.content, color = if (on) Paper else Ink, fontSize = 15.sp,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                                Text("${b.start}–${b.end} · ${b.type}",
                                    color = if (on) GrayLight else Gray, fontSize = 12.sp)
                            }
                            if (on) Icon(Icons.Filled.Check, null, tint = Paper, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            } else {
                LazyColumn {
                    items(apps) { p ->
                        val on = selApps[p] == true
                        Row(Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) Ink else Paper, RoundedCornerShape(12.dp))
                            .clickable { selApps[p] = !on }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(appLabel(prefs, context, p), color = if (on) Paper else Ink,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            if (on) Icon(Icons.Filled.Check, null, tint = Paper, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (step == 0) {
            FilledButton(
                if (!daysConsistent) "요일들의 시간표가 서로 달라요"
                else if (chosenBlocks.isEmpty()) "일정을 1개 이상 골라주세요" else "다음 · 앱 고르기 (${chosenBlocks.size}개)",
                enabled = daysConsistent && chosenBlocks.isNotEmpty() && selDays.isNotEmpty()
            ) { step = 1 }
        } else {
            FilledButton(
                if (chosenApps.isEmpty()) "앱을 1개 이상 골라주세요" else "저장",
                enabled = chosenApps.isNotEmpty()
            ) { onSave(chosenApps, chosenBlocks, daysMask(selDays)) }
        }
        BackText { if (step == 0) onBack() else step = 0 }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimeStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Gray, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepChip("−") { onChange((value - 30 + 1440) % 1440) }
            Text(hhmm(value), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp))
            StepChip("+") { onChange((value + 30) % 1440) }
        }
    }
}

// ---------------- 전체 제한 ----------------

@Composable
private fun EditTotalScreen(prefs: Prefs, current: Int, currentDays: Int, onBack: () -> Unit, onSave: (Int, Int) -> Unit) {
    BackHandler(enabled = true) { onBack() }
    var minutes by remember { mutableIntStateOf(if (current > 0) current else 180) }
    var selDays by remember { mutableStateOf(if (current > 0) maskToDays(currentDays) else todayDaySet()) }
    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("전체 하루 제한", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("모든 허용 앱의 사용시간 합계 제한", style = MaterialTheme.typography.bodyMedium)
        if (prefs.timetablePerDay) { Spacer(Modifier.height(22.dp)); DayPicker(selDays) { selDays = it } }
        Spacer(Modifier.height(40.dp))
        Text(fmtMin(minutes), color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-30, -10, 10, 30).forEach { d ->
                StepChip(if (d > 0) "+$d" else "$d") { minutes = (minutes + d).coerceIn(10, 1440) }
            }
        }
        Spacer(Modifier.weight(1f))
        FilledButton("저장", enabled = selDays.isNotEmpty()) { onSave(minutes, daysMask(selDays)) }
        BackText(onBack)
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------- 공통 ----------------

@Composable
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(Modifier.background(Paper, RoundedCornerShape(12.dp)).border(1.dp, GrayLight, RoundedCornerShape(12.dp))
        .repeatingClickable(onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilledButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (enabled) Ink else GrayLight, RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Paper, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BackText(onBack: () -> Unit) {
    Spacer(Modifier.height(4.dp))
    Text("뒤로", color = Gray, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(14.dp), textAlign = TextAlign.Center)
}

// ---------------- 잠긴 규칙용 PIN ----------------

@Composable
private fun StPinGate(verify: (String) -> Boolean, onSuccess: () -> Unit, onCancel: () -> Unit) {
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
    Column(Modifier.fillMaxSize().background(Paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(64.dp))
        Text("복구코드 입력", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(if (error) "코드가 일치하지 않습니다" else "굳은 규칙은 복구코드로만 수정/삭제돼요",
            style = MaterialTheme.typography.bodyMedium, color = if (error) Ink else Gray)
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            PinBoxes(text = entered, error = error)
            BasicTextField(
                value = entered,
                onValueChange = { entered = Prefs.normalizePinInput(it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = TextStyle(color = Color.Transparent),
                modifier = Modifier.matchParentSize().focusRequester(focus)
            )
        }
        Spacer(Modifier.weight(1f))
        Text("취소", color = Gray, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable(onClick = onCancel).padding(12.dp))
        Spacer(Modifier.height(16.dp))
    }
}
