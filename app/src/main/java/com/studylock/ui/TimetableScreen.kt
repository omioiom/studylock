package com.studylock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylock.DateUtil
import com.studylock.Prefs
import com.studylock.TimeBlock
import com.studylock.Timetable
import com.studylock.TimetableLoader
import com.studylock.ScheduleNotifier
import kotlinx.coroutines.delay

private enum class TtSub { VIEW, SETTINGS, EDITOR, EDIT_LIST, EDIT_BLOCK }

private val VIEW_NAMES = listOf("리스트", "타임라인", "카드", "집중", "한눈에")

private fun nowMinutes(): Int {
    val t = java.time.LocalTime.now()
    return t.hour * 60 + t.minute
}

private fun fmtRange(b: TimeBlock) = "${b.start}–${b.end}"

private fun remainMin(b: TimeBlock, now: Int) = (b.endMin - now).coerceAtLeast(0)

@Composable
fun TimetableRoot(prefs: Prefs, onClose: () -> Unit) {
    val context = LocalContext.current

    var rawJson by remember { mutableStateOf(TimetableLoader.activeJson(context, prefs)) }
    val parsed = remember(rawJson) { TimetableLoader.parse(rawJson) }
    val timetable = parsed.getOrNull() ?: Timetable("시간표 불러오기 실패", emptyList())

    var sub by remember { mutableStateOf(TtSub.VIEW) }
    var viewMode by remember { mutableIntStateOf(prefs.ttViewMode.coerceIn(0, 4)) }
    var dday by remember { mutableIntStateOf(prefs.ttDday) }
    var dimPast by remember { mutableStateOf(prefs.ttDimPast) }
    var autoScroll by remember { mutableStateOf(prefs.ttAutoScroll) }
    var perDay by remember { mutableStateOf(prefs.timetablePerDay) }
    var notifyStart by remember { mutableStateOf(prefs.ttNotifyStart) }
    var notifyEnd by remember { mutableStateOf(prefs.ttNotifyEnd) }

    // 편집 상태
    var editDow by remember { mutableIntStateOf(TimetableLoader.todayDow()) }
    var editTitle by remember { mutableStateOf("") }
    var editBlocks by remember { mutableStateOf<List<TimeBlock>>(emptyList()) }
    var editIndex by remember { mutableIntStateOf(-1) }
    // 편집 목록 스크롤 상태(블록 수정 후 그 위치 유지) + 요일별 복붙 클립보드
    val editListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var clipboard by remember { mutableStateOf<Pair<String, List<TimeBlock>>?>(null) }

    fun refreshView() { rawJson = TimetableLoader.activeJson(context, prefs) }
    fun loadEdit() {
        val t = TimetableLoader.parse(TimetableLoader.editTargetJson(context, prefs, editDow)).getOrNull()
            ?: Timetable("시간표", emptyList())
        editTitle = t.title; editBlocks = t.blocks
    }
    fun saveEdit(blocks: List<TimeBlock>, title: String = editTitle) {
        // 블록마다 안정 id 부여(스크린타임 일정별차단이 이름·시간 바껴도 이름 추적하게)
        val withIds = blocks.map { if (it.id.isBlank()) it.copy(id = java.util.UUID.randomUUID().toString().take(8)) else it }
        val sorted = withIds.sortedBy { it.startMin }
        editTitle = title; editBlocks = sorted
        TimetableLoader.saveTarget(prefs, editDow, TimetableLoader.serialize(Timetable(title, sorted)))
        refreshView()
        ScheduleNotifier.reschedule(context, prefs)   // 시간표 바뀌면 알림 재예약
    }

    var nowMin by remember { mutableIntStateOf(nowMinutes()) }
    LaunchedEffect(Unit) { while (true) { nowMin = nowMinutes(); delay(20_000) } }

    BackHandler(enabled = true) {
        when (sub) {
            TtSub.VIEW -> onClose()
            TtSub.EDIT_BLOCK -> sub = TtSub.EDIT_LIST
            TtSub.EDIT_LIST, TtSub.EDITOR -> sub = TtSub.SETTINGS
            else -> sub = TtSub.VIEW
        }
    }

    when (sub) {
        TtSub.VIEW -> TtViewScreen(
            timetable = timetable,
            parseError = parsed.exceptionOrNull()?.message,
            ddayDays = DateUtil.daysUntil(prefs.targetDateMillis).toInt(),
            nowMin = nowMin, viewMode = viewMode, dday = dday, dimPast = dimPast, autoScroll = autoScroll,
            onViewMode = { viewMode = it; prefs.ttViewMode = it },
            onClose = onClose, onSettings = { sub = TtSub.SETTINGS }
        )
        TtSub.SETTINGS -> TtSettingsScreen(
            dday = dday, dimPast = dimPast, autoScroll = autoScroll, perDay = perDay,
            notifyStart = notifyStart, notifyEnd = notifyEnd,
            onDday = { dday = it; prefs.ttDday = it },
            onDimPast = { dimPast = it; prefs.ttDimPast = it },
            onAutoScroll = { autoScroll = it; prefs.ttAutoScroll = it },
            onPerDay = { perDay = it; prefs.timetablePerDay = it; refreshView() },
            onNotifyStart = { notifyStart = it; prefs.ttNotifyStart = it; ScheduleNotifier.reschedule(context, prefs) },
            onNotifyEnd = { notifyEnd = it; prefs.ttNotifyEnd = it; ScheduleNotifier.reschedule(context, prefs) },
            onEditUi = { editDow = TimetableLoader.todayDow(); loadEdit(); sub = TtSub.EDIT_LIST },
            onEditJson = { sub = TtSub.EDITOR },
            onResetDefault = {
                prefs.timetableJson = null; prefs.timetableByDayJson = "{}"
                refreshView(); ScheduleNotifier.reschedule(context, prefs); sub = TtSub.VIEW
            },
            onBack = { sub = TtSub.VIEW }
        )
        TtSub.EDIT_LIST -> EditListScreen(
            perDay = perDay, editDow = editDow, title = editTitle, blocks = editBlocks,
            context = context, prefs = prefs, listState = editListState,
            hasClipboard = clipboard != null,
            onDow = { editDow = it; loadEdit() },
            onTitle = { saveEdit(editBlocks, it) },
            onAdd = { editIndex = -1; sub = TtSub.EDIT_BLOCK },
            onEditBlock = { i -> editIndex = i; sub = TtSub.EDIT_BLOCK },
            onDelete = { i -> saveEdit(editBlocks.filterIndexed { idx, _ -> idx != i }) },
            onCopy = { clipboard = editTitle to editBlocks },
            onPaste = { clipboard?.let { saveEdit(it.second, it.first) } },
            onBack = { sub = TtSub.SETTINGS }
        )
        TtSub.EDIT_BLOCK -> BlockFormScreen(
            prefs = prefs,
            initial = editBlocks.getOrNull(editIndex),
            onSave = { b ->
                val list = if (editIndex >= 0) editBlocks.mapIndexed { i, o -> if (i == editIndex) b else o }
                else editBlocks + b
                saveEdit(list); sub = TtSub.EDIT_LIST
            },
            onDelete = if (editIndex >= 0) {
                { saveEdit(editBlocks.filterIndexed { i, _ -> i != editIndex }); sub = TtSub.EDIT_LIST }
            } else null,
            onBack = { sub = TtSub.EDIT_LIST }
        )
        TtSub.EDITOR -> TtEditorScreen(
            initial = if (perDay) TimetableLoader.weekJson(context, prefs)
                      else TimetableLoader.prettyJson(TimetableLoader.editTargetJson(context, prefs, TimetableLoader.todayDow())),
            perDay = perDay,
            prefs = prefs,
            onSaved = { perDay = prefs.timetablePerDay; refreshView(); sub = TtSub.VIEW },
            onBack = { sub = TtSub.SETTINGS }
        )
    }
}

// ============================ 보기 ============================

@Composable
private fun TtViewScreen(
    timetable: Timetable,
    parseError: String?,
    ddayDays: Int,
    nowMin: Int,
    viewMode: Int,
    dday: Int,
    dimPast: Boolean,
    autoScroll: Boolean,
    onViewMode: (Int) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit
) {
    val blocks = timetable.blocks
    val curIdx = TimetableLoader.currentIndex(blocks, nowMin)

    Column(Modifier.fillMaxSize().background(Paper)) {

        // ---- 상단바 ----
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("닫기", color = Gray, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onClose).padding(vertical = 6.dp, horizontal = 2.dp))
            Text(timetable.title, color = Ink, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text("설정", color = Gray, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onSettings).padding(vertical = 6.dp, horizontal = 2.dp))
        }

        // ---- D-day + 지금 할 일 (집중 모드 제외) ----
        if (dday != 0 && viewMode != 3) {
            DdayBar(ddayDays, big = dday == 2)
            if (curIdx >= 0) NowStrip(blocks[curIdx], nowMin)
        }
        // 현재 잡힌 일정이 없으면 다음 일정까지 남은 시간 표시
        if (curIdx < 0 && viewMode != 3 && blocks.isNotEmpty()) NextStrip(blocks, nowMin)

        // ---- 디자인 탭 ----
        DesignTabs(viewMode, onViewMode)

        Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))

        // ---- 본문 ----
        if (parseError != null) {
            ErrorBody(parseError)
        } else if (blocks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("시간표가 비어있어요. 설정 → JSON 수정에서 추가하세요.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else when (viewMode) {
            0 -> ListView(blocks, curIdx, nowMin, dimPast, autoScroll)
            1 -> TimelineView(blocks, curIdx, nowMin, dimPast, autoScroll)
            2 -> CardsView(blocks, curIdx, nowMin, dimPast, autoScroll)
            3 -> FocusView(blocks, curIdx, nowMin, ddayDays)
            else -> CompactView(blocks, curIdx, nowMin, dimPast, autoScroll)
        }
    }
}

@Composable
private fun DdayBar(days: Int, big: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (big) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(Prefs(androidx.compose.ui.platform.LocalContext.current).ddayLabel,
            color = Gray, fontSize = if (big) 16.sp else 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            if (days >= 0) "D-$days" else "D+${-days}",
            color = Ink, fontWeight = FontWeight.Bold,
            fontSize = if (big) 40.sp else 18.sp,
            letterSpacing = (-1).sp
        )
    }
}

@Composable
private fun NowStrip(b: TimeBlock, now: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(Ink, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("지금  ", color = Gray, fontSize = 13.sp)
        Text(b.content, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("${remainMin(b, now)}분 남음", color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun NextStrip(blocks: List<TimeBlock>, now: Int) {
    val nextIdx = TimetableLoader.nextIndex(blocks, now)
    val b = blocks.getOrNull(nextIdx) ?: return
    val mins = (b.startMin - now).let { if (it < 0) it + 1440 else it }
    val until = if (mins >= 60) "${mins / 60}시간 ${mins % 60}분" else "${mins}분"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(GrayLight, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("다음 일정까지 $until", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("${b.content} · ${b.start}", color = Gray, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DesignTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VIEW_NAMES.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Ink else Paper, RoundedCornerShape(20.dp))
                    .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(name, color = if (on) Paper else Gray, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ErrorBody(msg: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("JSON 을 읽을 수 없어요", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(msg, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("설정 → JSON 수정에서 고치세요.", style = MaterialTheme.typography.bodyMedium)
    }
}

// ---- 카테고리 태그 ----
@Composable
private fun TypeTag(type: String, inverted: Boolean = false) {
    if (type.isBlank()) return
    val fg = if (inverted) Paper else Gray
    Box(
        Modifier.border(1.dp, if (inverted) Paper else GrayLight, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(type, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- 0. 리스트 ----------
@Composable
private fun ListView(blocks: List<TimeBlock>, curIdx: Int, now: Int, dimPast: Boolean, autoScroll: Boolean) {
    val state = rememberLazyListState()
    AutoScroll(state, curIdx, autoScroll)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)) {
        itemsIndexed(blocks) { i, b ->
            val cur = i == curIdx
            val past = dimPast && curIdx >= 0 && i < curIdx
            Row(
                Modifier.fillMaxWidth()
                    .background(if (cur) Ink else Paper, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    fmtRange(b),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    maxLines = 1, softWrap = false,
                    color = if (cur) Paper else if (past) GrayLight else Gray,
                    modifier = Modifier.width(88.dp)
                )
                Text(
                    b.content, fontSize = 15.sp,
                    fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (cur) Paper else if (past) GrayLight else Ink,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TypeTag(b.type, inverted = cur)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ---------- 1. 타임라인 ----------
@Composable
private fun TimelineView(blocks: List<TimeBlock>, curIdx: Int, now: Int, dimPast: Boolean, autoScroll: Boolean) {
    val state = rememberLazyListState()
    AutoScroll(state, curIdx, autoScroll)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
        itemsIndexed(blocks) { i, b ->
            val cur = i == curIdx
            val past = dimPast && curIdx >= 0 && i < curIdx
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                // 레일
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                    Box(Modifier.width(2.dp).height(6.dp).background(if (i == 0) Paper else GrayLight))
                    Box(
                        Modifier.size(if (cur) 16.dp else 10.dp)
                            .background(if (cur) Ink else Paper, CircleShape)
                            .border(if (cur) 0.dp else 2.dp, if (past) GrayLight else Ink, CircleShape)
                    )
                    Box(Modifier.width(2.dp).weight(1f).background(GrayLight))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(bottom = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtRange(b), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            color = if (past) GrayLight else Gray)
                        Spacer(Modifier.width(8.dp))
                        TypeTag(b.type)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(b.content, fontSize = 15.sp,
                        fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                        color = if (cur) Ink else if (past) GrayLight else Ink)
                }
            }
        }
    }
}

// ---------- 2. 카드 ----------
@Composable
private fun CardsView(blocks: List<TimeBlock>, curIdx: Int, now: Int, dimPast: Boolean, autoScroll: Boolean) {
    val state = rememberLazyListState()
    AutoScroll(state, curIdx, autoScroll)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)) {
        itemsIndexed(blocks) { i, b ->
            val cur = i == curIdx
            val past = dimPast && curIdx >= 0 && i < curIdx
            Column(
                Modifier.fillMaxWidth()
                    .background(if (cur) Ink else Paper, RoundedCornerShape(16.dp))
                    .border(1.dp, if (cur) Ink else GrayLight, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtRange(b), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = if (cur) Paper else if (past) GrayLight else Gray)
                    Spacer(Modifier.weight(1f))
                    if (cur) Text("지금", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    else TypeTag(b.type)
                }
                Spacer(Modifier.height(10.dp))
                Text(b.content, fontSize = 18.sp, lineHeight = 25.sp,
                    fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (cur) Paper else if (past) GrayLight else Ink)
                if (cur) {
                    Spacer(Modifier.height(8.dp))
                    Text("${remainMin(b, now)}분 남음", color = Paper, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------- 3. 집중 (지금 할 일 크게) ----------
@Composable
private fun FocusView(blocks: List<TimeBlock>, curIdx: Int, now: Int, ddayDays: Int) {
    val nextIdx = TimetableLoader.nextIndex(blocks, now)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val ddL = Prefs(androidx.compose.ui.platform.LocalContext.current).ddayLabel
        Text(if (ddayDays >= 0) "$ddL D-$ddayDays" else "$ddL D+${-ddayDays}",
            color = Gray, fontSize = 14.sp)
        Spacer(Modifier.height(40.dp))
        if (curIdx >= 0) {
            val b = blocks[curIdx]
            Text("지금 할 일", color = Gray, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Text(b.content, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                lineHeight = 44.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text("${fmtRange(b)}  ·  ${remainMin(b, now)}분 남음", color = Gray, fontSize = 15.sp)
            TypeTagBig(b.type)
        } else {
            Text("지금은 잡힌 일정이 없어요", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
        }
        if (nextIdx >= 0) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
            Spacer(Modifier.height(20.dp))
            Text("다음", color = Gray, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("${blocks[nextIdx].start}  ${blocks[nextIdx].content}",
                color = Gray, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TypeTagBig(type: String) {
    if (type.isBlank()) return
    Spacer(Modifier.height(16.dp))
    Box(Modifier.background(Ink, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(type, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- 4. 한눈에 (조밀) ----------
@Composable
private fun CompactView(blocks: List<TimeBlock>, curIdx: Int, now: Int, dimPast: Boolean, autoScroll: Boolean) {
    val state = rememberLazyListState()
    AutoScroll(state, curIdx, autoScroll)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        itemsIndexed(blocks) { i, b ->
            val cur = i == curIdx
            val past = dimPast && curIdx >= 0 && i < curIdx
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(20.dp)
                    .background(if (cur) Ink else Paper, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text(b.start, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    color = if (cur) Ink else if (past) GrayLight else Gray, modifier = Modifier.width(46.dp))
                Text(b.content, fontSize = 13.sp,
                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    color = if (cur) Ink else if (past) GrayLight else Ink,
                    maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AutoScroll(state: androidx.compose.foundation.lazy.LazyListState, curIdx: Int, enabled: Boolean) {
    LaunchedEffect(curIdx, enabled) {
        if (enabled && curIdx >= 0) {
            runCatching { state.animateScrollToItem(curIdx.coerceAtLeast(0)) }
        }
    }
}

// ============================ 설정 ============================

@Composable
private fun TtSettingsScreen(
    dday: Int, dimPast: Boolean, autoScroll: Boolean, perDay: Boolean,
    notifyStart: Boolean, notifyEnd: Boolean,
    onDday: (Int) -> Unit, onDimPast: (Boolean) -> Unit, onAutoScroll: (Boolean) -> Unit,
    onPerDay: (Boolean) -> Unit, onNotifyStart: (Boolean) -> Unit, onNotifyEnd: (Boolean) -> Unit,
    onEditUi: () -> Unit, onEditJson: () -> Unit, onResetDefault: () -> Unit, onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Paper).padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(28.dp))
        Text("시간표 설정", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
        Spacer(Modifier.height(24.dp))

        SettingLabel("시간표 안 D-day 표시")
        SegmentRow(listOf("끄기", "작게", "크게"), dday, onDday)
        Spacer(Modifier.height(20.dp))

        ToggleRow("지난 일정 흐리게", dimPast, onDimPast)
        Spacer(Modifier.height(8.dp))
        ToggleRow("현재 일정 자동 스크롤", autoScroll, onAutoScroll)
        Spacer(Modifier.height(8.dp))
        ToggleRow("요일별 다르게", perDay, onPerDay)
        Spacer(Modifier.height(2.dp))
        Text("켜면 월~일 각각 다른 시간표를 짤 수 있어요. (끄면 모든 요일 동일)",
            style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
        Spacer(Modifier.height(20.dp))

        SettingLabel("일정 알림")
        ToggleRow("일정 시작 알림", notifyStart, onNotifyStart)
        Spacer(Modifier.height(8.dp))
        ToggleRow("일정 종료 알림", notifyEnd, onNotifyEnd)
        Spacer(Modifier.height(2.dp))
        Text("일정이 시작·끝날 때 상단에 알림이 떠요.", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))
        Spacer(Modifier.height(20.dp))

        ActionRow("시간표 편집", "블록을 추가·수정·삭제 (UI)", onEditUi)
        Spacer(Modifier.height(12.dp))
        ActionRow("JSON 직접 수정", "고급: JSON 으로 한 번에 편집", onEditJson)
        Spacer(Modifier.height(12.dp))
        ActionRow("기본 시간표로 되돌리기", "내가 넣은 내용을 지우고 기본값으로", onResetDefault)

        Spacer(Modifier.height(32.dp))
        Text("뒤로", color = Gray, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(16.dp),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingLabel(t: String) {
    Text(t, color = Ink, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SegmentRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Ink else Paper, RoundedCornerShape(12.dp))
                    .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(name, color = if (on) Paper else Gray, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier.width(50.dp).height(28.dp)
                .background(if (value) Ink else Paper, RoundedCornerShape(14.dp))
                .border(1.5.dp, if (value) Ink else GrayLight, RoundedCornerShape(14.dp)),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(3.dp).size(22.dp)
                .background(if (value) Paper else GrayLight, CircleShape))
        }
    }
}

@Composable
private fun ActionRow(title: String, desc: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, GrayLight, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(18.dp)
    ) {
        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(3.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium)
    }
}

// ============================ JSON 에디터 ============================

@Composable
private fun TtEditorScreen(initial: String, perDay: Boolean, prefs: Prefs, onSaved: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("AI로 시간표 만들기", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("① ChatGPT·Claude 와 내 목표·공부 상황을 편하게 얘기해요.\n" +
             "② 아래 프롬프트를 복사해 붙여넣어요.\n" +
             "③ AI가 준 응답(JSON)을 그대로 복사해 여기에 붙여넣으면 끝!" +
             if (perDay) "\n\n요일별 모드: 월~일 7일치 시간표를 한 번에 만들어요." else "",
            style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
        Spacer(Modifier.height(12.dp))

        // AI 프롬프트 복사
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Ink, RoundedCornerShape(12.dp))
                .clickable {
                    TimetableLoader.copyToClipboard(context, "AI 프롬프트", TimetableLoader.aiPrompt(perDay))
                    copied = true
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (copied) "복사됨" else "프롬프트 복사",
                color = Paper, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(14.dp))

        // 편집 영역
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(GrayField, RoundedCornerShape(12.dp))
                .border(1.dp, if (error != null) Ink else GrayLight, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; error = null },
                textStyle = TextStyle(
                    color = Ink, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 19.sp
                ),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.fillMaxSize()
            )
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text("오류: $error", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, GrayLight, RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("취소", color = Gray, style = MaterialTheme.typography.labelLarge) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Ink, RoundedCornerShape(14.dp))
                    .clickable {
                        if (TimetableLoader.isWeekJson(text)) {
                            val r = TimetableLoader.applyWeek(prefs, text)
                            if (r.isSuccess) onSaved() else error = r.exceptionOrNull()?.message ?: "형식 오류"
                        } else {
                            val r = TimetableLoader.parse(text)
                            if (r.isSuccess) {
                                TimetableLoader.saveTarget(prefs, TimetableLoader.todayDow(), text); onSaved()
                            } else error = r.exceptionOrNull()?.message ?: "형식 오류"
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("저장", color = Paper, style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ============================ 시간표 편집 (UI) ============================

private fun parseHHMM(s: String): Int {
    val p = s.trim().split(":"); val h = p.getOrNull(0)?.toIntOrNull() ?: 0; val m = p.getOrNull(1)?.toIntOrNull() ?: 0
    return (h * 60 + m).coerceIn(0, 1440)
}
private fun minToHHMM(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

@Composable
private fun EditListScreen(
    perDay: Boolean, editDow: Int, title: String, blocks: List<TimeBlock>,
    context: android.content.Context, prefs: Prefs,
    listState: androidx.compose.foundation.lazy.LazyListState, hasClipboard: Boolean,
    onDow: (Int) -> Unit, onTitle: (String) -> Unit,
    onAdd: () -> Unit, onEditBlock: (Int) -> Unit, onDelete: (Int) -> Unit,
    onCopy: () -> Unit, onPaste: () -> Unit, onBack: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    var confirmPaste by remember { mutableStateOf(false) }

    confirmDelete?.let { i ->
        ConfirmDialog(
            title = "이 블록을 삭제할까요?",
            message = blocks.getOrNull(i)?.let { "${it.start}–${it.end}  ${it.content}" },
            confirmLabel = "삭제",
            onConfirm = { onDelete(i) },
            onDismiss = { confirmDelete = null }
        )
    }
    if (confirmPaste) {
        ConfirmDialog(
            title = "여기에 붙여넣을까요?",
            message = "복사한 시간표로 이 요일을 덮어써요.",
            confirmLabel = "붙여넣기",
            onConfirm = onPaste,
            onDismiss = { confirmPaste = false }
        )
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("뒤로", color = Gray, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onBack).padding(6.dp))
            Text("시간표 편집", color = Ink, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text("+ 추가", color = Ink, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAdd).padding(6.dp))
        }

        if (perDay) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimetableLoader.DOW_NAMES.forEachIndexed { i, name ->
                    val dow = i + 1
                    TtChip(name, dow == editDow) { onDow(dow) }
                }
            }
            // 요일별 복사/붙여넣기
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallOutlineBtn("이 요일 복사", Modifier.weight(1f), enabled = blocks.isNotEmpty(), onClick = onCopy)
                SmallOutlineBtn("여기에 붙여넣기", Modifier.weight(1f), enabled = hasClipboard) { confirmPaste = true }
            }
        }

        // 제목
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            EditField(title, "제목", singleLine = true, onChange = onTitle)
        }

        if (blocks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("블록이 없어요. '+ 추가'로 만드세요.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                itemsIndexed(blocks) { i, b ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, GrayLight, RoundedCornerShape(12.dp))
                        .clickable { onEditBlock(i) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${b.start}–${b.end}", fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                    maxLines = 1, softWrap = false, color = Gray)
                                Spacer(Modifier.width(8.dp)); TypeTag(b.type)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(b.content, color = Ink, fontSize = 15.sp)
                        }
                        Text("삭제", color = Gray, fontSize = 13.sp,
                            modifier = Modifier.clickable { confirmDelete = i }.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallOutlineBtn(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(11.dp)).border(1.dp, if (enabled) Ink else GrayLight, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Ink else GrayLight, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun BlockFormScreen(prefs: Prefs, initial: TimeBlock?, onSave: (TimeBlock) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit) {
    var startM by remember { mutableIntStateOf(initial?.let { parseHHMM(it.start) } ?: 9 * 60) }
    var endM by remember { mutableIntStateOf(initial?.let { val e = parseHHMM(it.end); val s = parseHHMM(it.start); if (e <= s) e + 1440 else e } ?: 10 * 60) }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var types by remember { mutableStateOf(prefs.timetableTypes) }
    var type by remember { mutableStateOf(initial?.type?.ifBlank { types.firstOrNull() ?: "영어" } ?: (types.firstOrNull() ?: "영어")) }
    var adding by remember { mutableStateOf(false) }
    var newType by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteType by remember { mutableStateOf<String?>(null) }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "이 블록을 삭제할까요?",
            message = if (content.isNotBlank()) "${minToHHMM(startM)}–${minToHHMM(endM)}  $content" else null,
            confirmLabel = "삭제",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
    confirmDeleteType?.let { del ->
        ConfirmDialog(
            title = "유형 '$del' 삭제",
            message = "이 유형을 목록에서 삭제합니다. (블록 내용은 그대로예요)",
            confirmLabel = "삭제",
            onConfirm = {
                types = types.filter { it != del }
                prefs.timetableTypes = types
                if (type == del) type = types.firstOrNull() ?: "영어"
            },
            onDismiss = { confirmDeleteType = null }
        )
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Text(if (initial == null) "블록 추가" else "블록 수정",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(20.dp))

        TtTimeStepper("시작 시간", startM) {
            startM = it.coerceIn(0, 1440)
            if (endM <= startM) endM = startM + 5
            if (endM > startM + 1440) endM = startM + 1440
        }
        Spacer(Modifier.height(14.dp))
        // 종료는 시작+5분 ~ 시작+24h. 24:00 을 넘으면 '익일 HH:mm' (자정 넘김 블록)
        TtTimeStepper("종료 시간", endM,
            display = { if (it > 1440) "익일 " + minToHHMM(it - 1440) else minToHHMM(it) }
        ) { endM = it.coerceIn(startM + 5, startM + 1440) }
        Spacer(Modifier.height(24.dp))

        Text("내용", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        EditField(content, "예: 마더텅 고1 독해 (유형 풀이)", singleLine = false, onChange = { content = it })
        Spacer(Modifier.height(24.dp))

        Text("유형", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { t -> TtChip(t, t == type, onLongClick = { confirmDeleteType = t }) { type = t } }
            TtChip("+ 추가", false) { adding = true }
        }
        if (adding) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { EditField(newType, "새 유형 이름", singleLine = true, onChange = { newType = it }) }
                Spacer(Modifier.width(8.dp))
                Text("추가", color = Ink, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val n = newType.trim()
                        if (n.isNotBlank() && n !in types) {
                            types = types + n; prefs.timetableTypes = types; type = n
                        }
                        newType = ""; adding = false
                    }.padding(10.dp))
            }
        }
        Spacer(Modifier.height(28.dp))

        PrimaryButton("저장", enabled = content.isNotBlank() && endM > startM) {
            onSave(TimeBlock(minToHHMM(startM % 1440), minToHHMM(endM % 1440), content.trim(), type, initial?.id ?: ""))
        }
        if (onDelete != null) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, GrayLight, RoundedCornerShape(14.dp))
                .clickable { confirmDelete = true }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text("삭제", color = Ink, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("취소", color = Gray, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(14.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun TtChip(label: String, on: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier
        .clip(shape)                                   // 리플이 둥근모양으로 잘리게(사각형 방지)
        .background(if (on) Ink else Paper)
        .border(1.dp, if (on) Ink else GrayLight, shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(label, color = if (on) Paper else Gray, fontSize = 13.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TtTimeStepper(label: String, value: Int, display: (Int) -> String = { minToHHMM(it) }, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Gray, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().border(1.dp, GrayLight, RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepperBtn("−") { onChange(value - 5) }
            Text(display(value), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            StepperBtn("+") { onChange(value + 5) }
        }
    }
}

@Composable
private fun StepperBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(Ink, RoundedCornerShape(11.dp)).repeatingClickable(onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun EditField(value: String, hint: String, singleLine: Boolean, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().background(GrayField, RoundedCornerShape(12.dp))
        .border(1.dp, GrayLight, RoundedCornerShape(12.dp)).padding(14.dp)) {
        if (value.isEmpty()) Text(hint, color = Gray, fontSize = 15.sp)
        BasicTextField(value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(Ink), modifier = Modifier.fillMaxWidth())
    }
}
