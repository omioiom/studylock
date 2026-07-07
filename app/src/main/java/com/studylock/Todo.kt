package com.studylock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * 할 일(TODO) 한 건. 날짜 기반 — 각 항목이 'date'(yyyy-MM-dd)를 가진다.
 * start/end 는 타임라인용(선택). 없으면 -1 = '시간 미정'.
 * status: 0=안함, 1=중간(진행중), 2=완료.
 * priority: 0=낮음, 1=보통, 2=높음.
 * repeat: 0=없음, 1=매일, 2=평일(월~금), 3=매주(같은 요일).
 */
data class TodoItem(
    val id: String,
    val title: String,
    val date: String,               // yyyy-MM-dd
    val note: String = "",
    val startMin: Int = -1,
    val endMin: Int = -1,
    val type: String = "",
    val priority: Int = 1,
    val status: Int = 0,
    val repeat: Int = 0,
    val createdAt: Long = 0L
) {
    val timed: Boolean get() = startMin in 0..1439
    val done: Boolean get() = status == 2
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

object TodoStore {

    const val STATUS_NONE = 0
    const val STATUS_MID = 1
    const val STATUS_DONE = 2

    const val REPEAT_NONE = 0
    const val REPEAT_DAILY = 1
    const val REPEAT_WEEKDAY = 2
    const val REPEAT_WEEKLY = 3

    /** 카테고리(시간표 type 재사용) */
    val TYPES = listOf("영어", "국어", "수학", "탐구", "사문", "복습", "숙제", "시험", "운동", "기타")

    fun statusLabel(s: Int) = when (s) {
        STATUS_DONE -> "완료"; STATUS_MID -> "중간"; else -> "안함"
    }

    fun priorityLabel(p: Int) = when (p) {
        2 -> "높음"; 0 -> "낮음"; else -> "보통"
    }

    fun repeatLabel(r: Int) = when (r) {
        REPEAT_DAILY -> "매일"; REPEAT_WEEKDAY -> "평일"; REPEAT_WEEKLY -> "매주"; else -> "반복 없음"
    }

    /** 상태 순환: 안함 → 중간 → 완료 → 안함 */
    fun nextStatus(s: Int) = (s + 1) % 3

    fun todayStr(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    fun hhmm(min: Int): String =
        if (min in 0..1439) "%02d:%02d".format(min / 60, min % 60) else "미정"

    // ---------- 직렬화 ----------

    fun parse(json: String?): List<TodoItem> {
        if (json.isNullOrBlank()) return emptyList()
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = o.optJSONArray("todos") ?: return emptyList()
        // 항목 하나가 깨져도 그것만 버리고 나머지는 유지
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val t = arr.getJSONObject(i)
                val title = t.getString("title")
                TodoItem(
                    id = t.optString("id", "").ifBlank { genId(i, title) },
                    title = title,
                    date = t.optString("date", todayStr()),
                    note = t.optString("note", ""),
                    startMin = t.optInt("startMin", -1),
                    endMin = t.optInt("endMin", -1),
                    type = t.optString("type", ""),
                    priority = t.optInt("priority", 1),
                    status = t.optInt("status", 0),
                    repeat = t.optInt("repeat", 0),
                    createdAt = t.optLong("createdAt", 0L)
                )
            }.getOrNull()
        }
    }

    fun serialize(items: List<TodoItem>): String {
        val arr = JSONArray()
        items.forEach { t ->
            arr.put(JSONObject()
                .put("id", t.id).put("title", t.title).put("date", t.date)
                .put("note", t.note).put("startMin", t.startMin).put("endMin", t.endMin)
                .put("type", t.type).put("priority", t.priority).put("status", t.status)
                .put("repeat", t.repeat).put("createdAt", t.createdAt))
        }
        return JSONObject().put("todos", arr).toString()
    }

    fun prettyJson(json: String): String = runCatching { JSONObject(json).toString(2) }.getOrDefault(json)

    /** 편집용 내보내기 JSON — 의미 있는 필드(title/date/type/note/status)만, 보기 좋게 들여쓰기.
     *  id·createdAt 등 내부 필드는 빼서 사람이 읽고 고치기 쉽게 한다(불러올 때 자동 재생성). */
    fun exportJson(items: List<TodoItem>): String {
        val arr = JSONArray()
        items.forEach { t ->
            val o = JSONObject().put("title", t.title).put("date", t.date)
            if (t.type.isNotBlank()) o.put("type", t.type)
            if (t.note.isNotBlank()) o.put("note", t.note)
            if (t.status != 0) o.put("status", t.status)
            arr.put(o)
        }
        return JSONObject().put("todos", arr).toString(2)
    }

    fun load(prefs: Prefs): List<TodoItem> = parse(prefs.todoJson)

    fun save(prefs: Prefs, items: List<TodoItem>) { prefs.todoJson = serialize(items) }

    /** id 없는(=AI/수기 JSON) 항목에 안정 id 부여 */
    private fun genId(index: Int, title: String): String =
        "t${System.currentTimeMillis()}_${index}_${title.hashCode()}"

    fun newId(): String = "t${System.currentTimeMillis()}_${(0..9999).random()}"

    /**
     * 붙여넣은 JSON 검증·파싱. {"todos":[...]} 또는 최상위가 배열([...])이어도 허용.
     * 실패 시 Result.failure.
     */
    fun parseImport(json: String): Result<List<TodoItem>> = runCatching {
        val trimmed = json.trim()
        val wrapped = if (trimmed.startsWith("[")) """{"todos":$trimmed}""" else trimmed
        val list = parse(wrapped)
        if (list.isEmpty()) throw IllegalArgumentException("할 일이 하나도 없어요")
        // 날짜 형식 검증(상대 날짜 지원)
        list.map { it.copy(date = normalizeDate(it.date), id = it.id.ifBlank { newId() }) }
    }

    /** "오늘/내일/모레" 또는 "+3"(3일 후) 같은 상대 날짜를 절대 날짜로 변환 */
    fun normalizeDate(raw: String): String {
        val s = raw.trim()
        val today = LocalDate.now(ZoneId.systemDefault())
        return when {
            s == "오늘" || s.equals("today", true) -> today.toString()
            s == "내일" || s.equals("tomorrow", true) -> today.plusDays(1).toString()
            s == "모레" -> today.plusDays(2).toString()
            s.startsWith("+") -> today.plusDays(s.drop(1).toLongOrNull() ?: 0).toString()
            runCatching { LocalDate.parse(s) }.isSuccess -> s
            else -> today.toString()   // 알 수 없으면 오늘
        }
    }

    // ---------- 유지보수: 반복 생성 + 미완료 이월 ----------

    /**
     * 앱 진입/날짜 변경 시 1회. 변경되면 true.
     * - 반복 항목: date < 오늘이면 다음 발생일(>= 오늘)로 굴리고 상태 초기화
     * - 미완료 이월(옵션): 반복 아닌 미완료 항목이 지난 날짜면 오늘로 당김
     */
    fun ensureFresh(prefs: Prefs): Boolean {
        val today = LocalDate.now(ZoneId.systemDefault())
        val items = load(prefs)
        if (items.isEmpty()) return false
        var changed = false
        val out = items.map { t ->
            val d = t.localDate ?: return@map t
            when {
                t.repeat != REPEAT_NONE && d.isBefore(today) -> {
                    changed = true
                    t.copy(date = nextOccurrence(d, t.repeat, today).toString(), status = STATUS_NONE)
                }
                prefs.todoCarryOver && t.repeat == REPEAT_NONE && !t.done && d.isBefore(today) -> {
                    changed = true
                    t.copy(date = today.toString())
                }
                else -> t
            }
        }
        if (changed) save(prefs, out)
        return changed
    }

    /** from 이후 규칙에 맞는 첫 날짜(>= today) */
    private fun nextOccurrence(from: LocalDate, repeat: Int, today: LocalDate): LocalDate {
        var d = if (from.isBefore(today)) today else from
        return when (repeat) {
            REPEAT_DAILY -> d
            REPEAT_WEEKLY -> {
                while (d.dayOfWeek != from.dayOfWeek) d = d.plusDays(1)
                d
            }
            REPEAT_WEEKDAY -> {
                while (d.dayOfWeek.value >= 6) d = d.plusDays(1)   // 토(6)·일(7) 건너뜀
                d
            }
            else -> d
        }
    }

    // ---------- AI 프롬프트 ----------

    val aiPrompt: String get() = """
지금까지 우리가 나눈 대화(내 목표·과목·시험 일정·해야 할 것)를 바탕으로, 내 할 일 목록을 아래 JSON 형식으로 정리해줘.

정보가 부족하면 JSON을 만들기 전에 나한테 먼저 물어봐 줘. (예: 마감일, 과목 등)

정보가 충분하면 **설명 없이 순수 JSON만** 출력해줘 — 앞뒤 말도, ``` 코드블록 표시도 빼고. 그 응답을 그대로 복사해서 앱에 붙여넣을 거야.

## JSON 형식 (이 구조 그대로)
{
  "todos": [
    {"title": "영어 단어 500개", "date": "오늘", "type": "영어", "note": "워드마스터 Day 1~5"}
  ]
}

## 규칙
- title: 구체적으로 (교재·분량 등)
- date: "yyyy-MM-dd" 또는 "오늘"/"내일"/"모레"/"+3"(3일 후). 모르면 "오늘".
- type: 영어·국어·수학·탐구·사문·복습·숙제·시험·운동·기타 중 하나 (선택)
- note: 메모(선택)
- 마지막 출력은 JSON만 (다른 말 없이)
""".trim()

    fun copyToClipboard(context: Context, label: String, text: String) =
        TimetableLoader.copyToClipboard(context, label, text)
}
