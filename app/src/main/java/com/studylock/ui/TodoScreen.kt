package com.studylock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.studylock.Prefs
import com.studylock.TodoItem
import com.studylock.TodoStore
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class TodoSub { MAIN, EDIT, IMPORT, SETTINGS }

private val DOW_KR = listOf("월", "화", "수", "목", "금", "토", "일")

private fun fmtDateKr(d: LocalDate): String =
    "%d월 %d일 (%s)".format(d.monthValue, d.dayOfMonth, DOW_KR[d.dayOfWeek.value - 1])

@Composable
fun TodoRoot(prefs: Prefs, onClose: () -> Unit) {
    val context = LocalContext.current
    // 화면 열 때마다 밀린 미완료를 오늘로 이월(반복도 굴림). 콜드 스타트에만 의존하지 않게.
    var items by remember { mutableStateOf(run { TodoStore.ensureFresh(prefs); TodoStore.load(prefs) }) }
    var sub by remember { mutableStateOf(TodoSub.MAIN) }
    var view by remember { mutableIntStateOf(prefs.todoView.coerceIn(0, 1)) }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneId.systemDefault())) }
    var importReplace by remember { mutableStateOf(false) }   // 불러오기 진입 시 '전부 교체'로 시작할지

    fun commit(next: List<TodoItem>) {
        items = next
        TodoStore.save(prefs, next)
    }

    when (sub) {
        TodoSub.MAIN -> TodoMain(
            prefs = prefs, items = items, view = view, selectedDate = selectedDate,
            onView = { view = it; prefs.todoView = it },
            onSelectDate = { selectedDate = it },
            onClose = onClose,
            onAdd = { editing = null; sub = TodoSub.EDIT },
            onAddOn = { d -> editing = TodoItem(id = "", title = "", date = d.toString()); sub = TodoSub.EDIT },
            onOpen = { editing = it; sub = TodoSub.EDIT },
            onSetStatus = { t, s ->
                commit(items.map { if (it.id == t.id) it.copy(status = s) else it })
            },
            onOpenImport = { importReplace = false; sub = TodoSub.IMPORT },
            onOpenSettings = { sub = TodoSub.SETTINGS }
        )

        TodoSub.EDIT -> TodoEditScreen(
            prefs = prefs,
            initial = editing,
            defaultDate = selectedDate,
            onSave = { item ->
                commit(if (items.any { it.id == item.id }) items.map { if (it.id == item.id) item else it }
                       else items + item)
                sub = TodoSub.MAIN
            },
            onDelete = editing?.let { e -> { commit(items.filterNot { it.id == e.id }); sub = TodoSub.MAIN } },
            onBack = { sub = TodoSub.MAIN }
        )

        TodoSub.IMPORT -> TodoImportScreen(
            current = items,
            startReplace = importReplace,
            onApply = { merged -> commit(merged); sub = TodoSub.MAIN },
            onBack = { sub = TodoSub.MAIN }
        )

        TodoSub.SETTINGS -> TodoSettingsScreen(
            prefs = prefs,
            onOpenJson = { importReplace = true; sub = TodoSub.IMPORT },
            onBack = { sub = TodoSub.MAIN }
        )
    }
}

// ============================ 메인 ============================

@Composable
private fun TodoMain(
    prefs: Prefs, items: List<TodoItem>, view: Int, selectedDate: LocalDate,
    onView: (Int) -> Unit, onSelectDate: (LocalDate) -> Unit,
    onClose: () -> Unit, onAdd: () -> Unit, onAddOn: (LocalDate) -> Unit,
    onOpen: (TodoItem) -> Unit, onSetStatus: (TodoItem, Int) -> Unit,
    onOpenImport: () -> Unit, onOpenSettings: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    // 상태 점 클릭 → 완료/중간/안함 선택 모달
    var statusFor by remember { mutableStateOf<TodoItem?>(null) }
    statusFor?.let { t ->
        StatusPickerDialog(
            item = t,
            onPick = { s -> onSetStatus(t, s); statusFor = null },
            onDismiss = { statusFor = null }
        )
    }
    val onCycle: (TodoItem) -> Unit = { statusFor = it }
    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize()) {
            // 헤더
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 26.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("할 일", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ink)
                Spacer(Modifier.weight(1f))
                Text("불러오기", color = Gray, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onOpenImport).padding(8.dp))
                Text("설정", color = Gray, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onOpenSettings).padding(8.dp))
                Text("닫기", color = Gray, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            // 뷰 토글
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("리스트", "캘린더").forEachIndexed { i, name ->
                    val on = i == view
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (on) Ink else Paper, RoundedCornerShape(12.dp))
                            .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(12.dp))
                            .clickable { onView(i) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = if (on) Paper else Gray, fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (view) {
                    1 -> CalendarBody(prefs, items, selectedDate, onSelectDate, onOpen, onCycle, onAddOn)
                    else -> ListBody(prefs, items, onOpen, onCycle)
                }
            }
        }
        // + 추가 버튼
        Box(
            Modifier.align(Alignment.BottomEnd).padding(24.dp).size(56.dp)
                .clip(CircleShape).background(Ink, CircleShape).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) { Text("+", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Light) }
    }
}

// ---------------- 리스트 뷰 ----------------

@Composable
private fun ListBody(prefs: Prefs, items: List<TodoItem>, onOpen: (TodoItem) -> Unit, onCycle: (TodoItem) -> Unit) {
    val visible = items.filter { prefs.todoShowDone || !it.done }
    if (visible.isEmpty()) { EmptyHint("할 일이 없어요.\n오른쪽 아래 + 로 추가하거나\n'불러오기'로 한번에 넣어보세요."); return }

    // 그룹핑
    val groups: List<Pair<String, List<TodoItem>>> = when (prefs.todoGroup) {
        1 -> listOf(
            "안함" to visible.filter { it.status == 0 },
            "중간" to visible.filter { it.status == 1 },
            "완료" to visible.filter { it.status == 2 }
        ).filter { it.second.isNotEmpty() }
        2 -> visible.groupBy { it.type.ifBlank { "기타" } }.toList().sortedBy { it.first }
        else -> visible.groupBy { it.date }.toList().sortedBy { it.first }
            .map { (d, list) -> groupDateLabel(d) to list }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        groups.forEach { (label, list) ->
            item(key = "h_$label") {
                Text(label, color = Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            }
            items(sortItems(prefs, list)) { t ->
                TodoRow(prefs, t, onOpen, onCycle)
                Spacer(Modifier.height(8.dp))
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

private fun groupDateLabel(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (d) {
        today -> "오늘 · ${fmtDateKr(d)}"
        today.plusDays(1) -> "내일 · ${fmtDateKr(d)}"
        else -> if (d.isBefore(today)) "지남 · ${fmtDateKr(d)}" else fmtDateKr(d)
    }
}

private fun sortItems(prefs: Prefs, list: List<TodoItem>): List<TodoItem> = when (prefs.todoSort) {
    1 -> list.sortedBy { it.type }        // 카테고리
    else -> list.sortedBy { it.createdAt } // 생성순(추가한 순)
}

@Composable
private fun TodoRow(prefs: Prefs, t: TodoItem, onOpen: (TodoItem) -> Unit, onCycle: (TodoItem) -> Unit) {
    val dim = prefs.todoDimDone && t.done
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, GrayLight, RoundedCornerShape(14.dp))
            .clickable { onOpen(t) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(t.status) { onCycle(t) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title.ifBlank { "(제목 없음)" },
                color = if (dim) Gray else Ink,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
            )
            if (t.type.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(t.type, color = Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusDot(status: Int, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).clickable(onClick = onClick)) { StatusGlyph(status) }
}

/** 상태 표시 원(클릭 없음). 0=빈원 1=반채움 2=완료(체크) */
@Composable
private fun StatusGlyph(status: Int) {
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .border(2.dp, if (status == 0) GrayLight else Ink, CircleShape)
            .background(if (status == 2) Ink else Paper, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            2 -> Text("✓", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            1 -> Box(Modifier.size(12.dp).clip(CircleShape).background(Ink))   // 중간=반쯤 채운 느낌
            else -> {}
        }
    }
}

/** 완료/중간/안함 선택 모달 — 각 옵션에 상태 원 + 설명, 현재 상태는 강조 */
@Composable
private fun StatusPickerDialog(item: TodoItem, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paper).padding(20.dp)) {
            Text(item.title.ifBlank { "상태 변경" }, color = Ink,
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            listOf(
                Triple(TodoStore.STATUS_DONE, "완료", "다 했어요"),
                Triple(TodoStore.STATUS_MID, "중간", "진행 중이에요"),
                Triple(TodoStore.STATUS_NONE, "안함", "아직 안 했어요")
            ).forEach { (s, label, desc) ->
                val cur = item.status == s
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (cur) GrayField else Paper, RoundedCornerShape(14.dp))
                        .border(1.dp, if (cur) Ink else GrayLight, RoundedCornerShape(14.dp))
                        .clickable { onPick(s) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusGlyph(s)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Ink, style = MaterialTheme.typography.titleMedium)
                        Text(desc, color = Gray, fontSize = 12.sp)
                    }
                    if (cur) Text("✓", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("닫기", color = Gray, style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss).padding(vertical = 12.dp))
        }
    }
}

// ---------------- 캘린더 뷰 ----------------

@Composable
private fun CalendarBody(
    prefs: Prefs, items: List<TodoItem>, selected: LocalDate,
    onSelect: (LocalDate) -> Unit, onOpen: (TodoItem) -> Unit, onCycle: (TodoItem) -> Unit,
    onAddOn: (LocalDate) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val today = LocalDate.now(ZoneId.systemDefault())
    val weekStart = prefs.todoWeekStart   // 0=월 1=일
    val dowHeader = if (weekStart == 0) DOW_KR else listOf("일") + DOW_KR.dropLast(1)
    val hasTodoDates = remember(items) { items.map { it.date }.toSet() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        // 월 네비
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Ink, fontSize = 28.sp,
                modifier = Modifier.clickable { month = month.minusMonths(1) }.padding(horizontal = 12.dp, vertical = 4.dp))
            Text("${month.year}년 ${month.monthValue}월", color = Ink, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("›", color = Ink, fontSize = 28.sp,
                modifier = Modifier.clickable { month = month.plusMonths(1) }.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        // 요일 헤더
        Row(Modifier.fillMaxWidth()) {
            dowHeader.forEach { Text(it, Modifier.weight(1f), color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }
        Spacer(Modifier.height(4.dp))
        // 날짜 그리드
        val first = month.atDay(1)
        val leadRaw = first.dayOfWeek.value - 1 - weekStart   // 월(0)~일(6) 기준 오프셋
        val lead = ((leadRaw % 7) + 7) % 7
        val daysInMonth = month.lengthOfMonth()
        val cells = lead + daysInMonth
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    val dayNum = idx - lead + 1
                    if (dayNum in 1..daysInMonth) {
                        val d = month.atDay(dayNum)
                        val hasTodos = d.toString() in hasTodoDates
                        val isSel = d == selected
                        val isToday = d == today
                        // 일정 있는 날 = 검은 테두리(2dp), 오늘 = 검은 테두리, 그 외 = 연한 테두리
                        val bColor = if (isSel || hasTodos || isToday) Ink else GrayLight
                        val bWidth = if (hasTodos && !isSel) 2.dp else 1.dp
                        Box(
                            Modifier.weight(1f).padding(2.dp).height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Ink else Paper, RoundedCornerShape(10.dp))
                                .border(bWidth, bColor, RoundedCornerShape(10.dp))
                                .clickable { onSelect(d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$dayNum", color = if (isSel) Paper else Ink, fontSize = 14.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        }
                    } else {
                        Box(Modifier.weight(1f).padding(2.dp).height(52.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 선택한 날 목록
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtDateKr(selected), color = Ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("+ 추가", color = Gray, fontSize = 13.sp, modifier = Modifier.clickable { onAddOn(selected) }.padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        val dayItems = sortItems(prefs, items.filter { it.date == selected.toString() && (prefs.todoShowDone || !it.done) })
        if (dayItems.isEmpty()) {
            Text("이 날 할 일이 없어요.", color = Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            dayItems.forEach { t -> TodoRow(prefs, t, onOpen, onCycle); Spacer(Modifier.height(8.dp)) }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun EmptyHint(msg: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = Gray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
    }
}

// ============================ 편집 ============================

@Composable
private fun TodoEditScreen(
    prefs: Prefs, initial: TodoItem?, defaultDate: LocalDate,
    onSave: (TodoItem) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    // 날짜는 편집 중이면 원래 날짜, 새로 추가면 선택돼 있던 날짜 그대로 (UI 없음)
    val fixedDate = initial?.date ?: defaultDate.toString()

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Text(if (initial?.title?.isNotBlank() == true) "할 일 수정" else "할 일 추가",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(runCatching { fmtDateKr(LocalDate.parse(fixedDate)) }.getOrDefault(fixedDate),
            color = Gray, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        FieldLabel("제목")
        PlainField(title, "무엇을 할까요?", false) { title = it }
        Spacer(Modifier.height(16.dp))

        // 카테고리
        FieldLabel("카테고리")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallChip("없음", type.isBlank()) { type = "" }
            TodoStore.TYPES.forEach { ty -> SmallChip(ty, type == ty) { type = ty } }
        }
        Spacer(Modifier.height(16.dp))

        // 메모
        FieldLabel("메모")
        PlainField(note, "메모(선택)", true) { note = it }
        Spacer(Modifier.height(20.dp))

        // 저장/삭제 — 날짜/시간/우선순위/반복은 기존 값 유지(없으면 기본값)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (title.isNotBlank()) Ink else GrayLight, RoundedCornerShape(14.dp))
                .clickable(enabled = title.isNotBlank()) {
                    val id = initial?.id?.ifBlank { TodoStore.newId() } ?: TodoStore.newId()
                    onSave(TodoItem(
                        id = id, title = title.trim(), date = fixedDate, note = note.trim(),
                        startMin = initial?.startMin ?: -1, endMin = initial?.endMin ?: -1,
                        type = type, priority = initial?.priority ?: 1, status = initial?.status ?: 0,
                        repeat = initial?.repeat ?: 0,
                        createdAt = initial?.createdAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
                    ))
                }.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) { Text("저장", color = Paper, style = MaterialTheme.typography.labelLarge) }

        if (onDelete != null) {
            Spacer(Modifier.height(10.dp))
            Text("삭제", color = Gray, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDelete).padding(12.dp), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        Text("뒤로", color = Gray, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(12.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
    }
}

// ============================ 불러오기 ============================

@Composable
private fun TodoImportScreen(
    current: List<TodoItem>, startReplace: Boolean,
    onApply: (List<TodoItem>) -> Unit, onBack: () -> Unit
) {
    val context = LocalContext.current
    var merge by remember { mutableStateOf(!startReplace) }   // true=기존에 추가, false=전부 교체
    // 전부 교체로 시작하면 기존 할 일을 JSON으로 미리 채워 편집 가능하게
    var text by remember { mutableStateOf(if (startReplace) TodoStore.exportJson(current) else "") }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    // 모드 전환: 전부 교체 → 기존 JSON 채움 / 기존에 추가 → 비움
    fun switchMode(toMerge: Boolean) {
        merge = toMerge
        error = null
        text = if (toMerge) "" else TodoStore.exportJson(current)
    }

    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("AI로 할 일 만들기", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("① ChatGPT·Claude 와 해야 할 것들을 얘기해요.\n② 아래 프롬프트를 복사해 붙여넣어요.\n③ AI가 준 JSON을 그대로 복사해 여기에 붙여넣으면 끝!\n('전부 교체'는 지금 할 일이 JSON으로 들어있어 직접 고칠 수도 있어요.)",
            style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink, RoundedCornerShape(12.dp))
                .clickable { TodoStore.copyToClipboard(context, "AI 프롬프트", TodoStore.aiPrompt); copied = true }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text(if (copied) "복사됨" else "프롬프트 복사", color = Paper, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.height(12.dp))

        // 병합/교체
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallChip("기존에 추가", merge) { switchMode(true) }
            SmallChip("전부 교체", !merge) { switchMode(false) }
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.fillMaxWidth().weight(1f).background(GrayField, RoundedCornerShape(12.dp))
                .border(1.dp, if (error != null) Ink else GrayLight, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            BasicTextField(
                value = text, onValueChange = { text = it; error = null },
                textStyle = TextStyle(color = Ink, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 19.sp),
                cursorBrush = SolidColor(Ink), modifier = Modifier.fillMaxSize()
            )
            if (text.isBlank()) Text("여기에 JSON을 붙여넣으세요", color = Gray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        if (error != null) { Spacer(Modifier.height(8.dp)); Text("오류: $error", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
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
                        val r = TodoStore.parseImport(text)
                        if (r.isSuccess) {
                            val imported = r.getOrThrow()
                            onApply(if (merge) current + imported else imported)
                        } else error = r.exceptionOrNull()?.message ?: "형식 오류"
                    }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("불러오기", color = Paper, style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ============================ 설정 ============================

@Composable
private fun TodoSettingsScreen(prefs: Prefs, onOpenJson: () -> Unit, onBack: () -> Unit) {
    BackHandler(enabled = true) { onBack() }
    var sort by remember { mutableIntStateOf(prefs.todoSort.coerceIn(0, 1)) }
    var group by remember { mutableIntStateOf(prefs.todoGroup) }
    var weekStart by remember { mutableIntStateOf(prefs.todoWeekStart) }
    var showDone by remember { mutableStateOf(prefs.todoShowDone) }
    var dimDone by remember { mutableStateOf(prefs.todoDimDone) }
    var carry by remember { mutableStateOf(prefs.todoCarryOver) }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("할 일 설정", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Ink)
            Spacer(Modifier.weight(1f))
            Text("닫기", color = Gray, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable(onClick = onBack).padding(6.dp))
        }
        Spacer(Modifier.height(20.dp))

        FieldLabel("리스트 정렬")
        Segment(listOf("생성순", "카테고리"), sort) { sort = it; prefs.todoSort = it }
        Spacer(Modifier.height(16.dp))

        FieldLabel("리스트 그룹핑")
        Segment(listOf("날짜", "상태", "카테고리"), group) { group = it; prefs.todoGroup = it }
        Spacer(Modifier.height(16.dp))

        FieldLabel("캘린더 주 시작")
        Segment(listOf("월요일", "일요일"), weekStart) { weekStart = it; prefs.todoWeekStart = it }
        Spacer(Modifier.height(8.dp))

        ToggleLine("완료한 할 일 표시", showDone) { showDone = it; prefs.todoShowDone = it }
        ToggleLine("완료·지난 항목 흐리게", dimDone) { dimDone = it; prefs.todoDimDone = it }
        ToggleLine("미완료 이월 (지난 할 일을 오늘로)", carry) { carry = it; prefs.todoCarryOver = it }
        Spacer(Modifier.height(24.dp))

        FieldLabel("데이터")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .border(1.dp, GrayLight, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenJson).padding(18.dp)
        ) {
            Text("할 일 전체 JSON으로 편집", color = Ink, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text("지금 할 일이 JSON으로 열려요. 직접 고치거나 통째로 바꿀 수 있어요.",
                color = Gray, fontSize = 13.sp)
        }
        Spacer(Modifier.height(30.dp))
    }
}

// ============================ 공통 소품 ============================

@Composable private fun FieldLabel(t: String) {
    Text(t, color = Ink, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
}

@Composable
private fun PlainField(value: String, hint: String, multi: Boolean, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(if (multi) 90.dp else 52.dp)
        .background(GrayField, RoundedCornerShape(12.dp))
        .border(1.dp, GrayLight, RoundedCornerShape(12.dp)).padding(14.dp)) {
        if (value.isBlank()) Text(hint, color = Gray, fontSize = 14.sp)
        BasicTextField(value = value, onValueChange = onChange, singleLine = !multi,
            textStyle = TextStyle(color = Ink, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(Ink), modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SmallChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (on) Ink else Paper, RoundedCornerShape(20.dp))
            .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = if (on) Paper else Gray, fontSize = 13.sp,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun Segment(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Ink else Paper, RoundedCornerShape(12.dp))
                    .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) { Text(name, color = if (on) Paper else Gray, fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun ToggleLine(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(Modifier.width(50.dp).height(28.dp)
            .background(if (value) Ink else Paper, RoundedCornerShape(14.dp))
            .border(1.5.dp, if (value) Ink else GrayLight, RoundedCornerShape(14.dp)),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(22.dp).background(if (value) Paper else GrayLight, CircleShape))
        }
    }
}

