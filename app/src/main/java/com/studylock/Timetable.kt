package com.studylock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 시간표 한 칸. start/end 는 "HH:MM" (24시간제, 24:00 허용). */
data class TimeBlock(
    val start: String,
    val end: String,
    val content: String,
    val type: String,
    val id: String = ""   // 스크린타임 일정별차단 연결용 안정 식별자(편집 시 부여)
) {
    val startMin: Int get() = parseMin(start)
    val endMin: Int get() = parseMin(end).let { e -> if (e <= startMin) e + 1440 else e }
}

data class Timetable(val title: String, val blocks: List<TimeBlock>)

private fun parseMin(hhmm: String): Int {
    val p = hhmm.trim().split(":")
    val h = p.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    return h * 60 + m
}

object TimetableLoader {
    private const val ASSET = "timetable.json"

    /** 카테고리 묶음 */
    val STUDY = setOf("영어", "사문", "국어", "수학", "탐구", "복습", "사회문화")
    val REST = setOf("휴식", "식사")

    fun defaultJson(context: Context): String =
        runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() } }
            .getOrDefault("{\"title\":\"시간표\",\"blocks\":[]}")

    /** 사용자가 저장한 JSON 우선, 없으면 기본 asset */
    fun currentJson(context: Context, saved: String?): String =
        saved?.takeIf { it.isNotBlank() } ?: defaultJson(context)

    val DOW_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

    /** 1=월 .. 7=일 */
    fun todayDow(): Int = java.time.LocalDate.now().dayOfWeek.value

    private fun byDayJson(prefs: Prefs, dow: Int): String? = runCatching {
        val o = JSONObject(prefs.timetableByDayJson ?: "{}")
        if (o.has(dow.toString())) o.getJSONObject(dow.toString()).toString() else null
    }.getOrNull()

    /** 지금 화면에 적용할 시간표 JSON (요일별이면 오늘 요일, 없으면 공유/asset) */
    fun activeJson(context: Context, prefs: Prefs): String {
        if (prefs.timetablePerDay) byDayJson(prefs, todayDow())?.let { return it }
        return currentJson(context, prefs.timetableJson)
    }

    /** 요일별 편집/보기용: 7일 각각 '실제 적용되는' JSON을 합친 주간 JSON(보기 좋게 들여쓰기).
     *  빈 요일은 공유/asset 로 채워져 화면에 보이는 것과 JSON 이 정확히 일치한다. */
    fun weekJson(context: Context, prefs: Prefs): String {
        val o = JSONObject()
        for (d in 1..7) o.put(d.toString(), JSONObject(editTargetJson(context, prefs, d)))
        return o.toString(2)
    }

    /** 단일 시간표 JSON을 보기 좋게(들여쓰기) */
    fun prettyJson(json: String): String = runCatching { JSONObject(json).toString(2) }.getOrDefault(json)

    /** 편집 대상 JSON (요일별 on이면 그 요일, 없으면 공유/asset 복사로 시작) */
    fun editTargetJson(context: Context, prefs: Prefs, dow: Int): String {
        if (prefs.timetablePerDay) byDayJson(prefs, dow)?.let { return it }
        return currentJson(context, prefs.timetableJson)
    }

    /** 편집 결과 저장 (요일별 on이면 그 요일, off면 공유) */
    fun saveTarget(prefs: Prefs, dow: Int, json: String) {
        if (prefs.timetablePerDay) {
            val o = runCatching { JSONObject(prefs.timetableByDayJson ?: "{}") }.getOrDefault(JSONObject())
            o.put(dow.toString(), JSONObject(json))
            prefs.timetableByDayJson = o.toString()
        } else {
            prefs.timetableJson = json
        }
    }

    fun serialize(t: Timetable): String {
        val o = JSONObject()
        o.put("title", t.title)
        o.put("blocks", JSONArray().apply {
            t.blocks.forEach { b ->
                val jo = JSONObject().put("start", b.start).put("end", b.end)
                    .put("content", b.content).put("type", b.type)
                if (b.id.isNotEmpty()) jo.put("id", b.id)
                put(jo)
            }
        })
        return o.toString()
    }

    /** 파싱(검증). 실패 시 Result.failure 로 이유 전달. */
    fun parse(json: String): Result<Timetable> = runCatching {
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("blocks")
        val blocks = (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            TimeBlock(
                start = b.getString("start"),
                end = b.getString("end"),
                content = b.getString("content"),
                type = b.optString("type", ""),
                id = b.optString("id", "")
            )
        }
        Timetable(obj.optString("title", "시간표"), blocks)
    }

    /** AI 에게 붙여넣을 프롬프트. perDay 면 요일별(7일) 구조를 요구한다. */
    fun aiPrompt(perDay: Boolean = false): String = if (perDay) weekPrompt else dayPrompt

    private val commonRules = """
## 규칙
- start / end 는 "HH:MM" 24시간제. 자정은 "24:00". 시간 겹치지 않게 하루 순서대로.
- type 종류: 영어, 국어, 수학, 탐구, 사문, 복습, 식사, 휴식, 운동, 이동, 기타
- content 는 구체적으로 (교재명·분량 등)
- 밥·휴식·이동 시간도 꼭 넣고, 공부 블록은 50~80분 + 휴식 5~15분 권장
- 마지막 출력은 JSON만 (다른 말 없이)
""".trim()

    private val dayPrompt: String get() = """
지금까지 우리가 나눈 대화(내 목표·과목·생활 패턴·고민)를 바탕으로, 내 하루 공부 시간표를 아래 JSON 형식으로 정리해줘.

혹시 시간표를 짜기에 정보가 부족하면, JSON을 만들기 전에 나한테 필요한 걸 먼저 물어봐 줘. (예: 기상·취침 시간, 과목별 비중, 꼭 넣을 일정 등)

정보가 충분하면 **설명 없이 순수 JSON만** 출력해줘 — 앞뒤 말도, ``` 코드블록 표시도 빼고. 그 응답을 내가 그대로 복사해서 앱에 붙여넣을 거야.

## JSON 형식 (이 구조 그대로)
{
  "title": "제목",
  "blocks": [
    {"start": "08:00", "end": "09:20", "content": "구체적인 할 일", "type": "영어"}
  ]
}

$commonRules
""".trim()

    private val weekPrompt: String get() = """
지금까지 우리가 나눈 대화(내 목표·과목·생활 패턴·고민)를 바탕으로, **요일별로 다른** 내 주간 공부 시간표를 아래 JSON 형식으로 정리해줘.

혹시 정보가 부족하면, JSON을 만들기 전에 나한테 필요한 걸 먼저 물어봐 줘. (예: 요일별 학원·일정, 기상·취침, 과목 비중 등)

정보가 충분하면 **설명 없이 순수 JSON만** 출력해줘 — 앞뒤 말도, ``` 코드블록 표시도 빼고.

## JSON 형식 (요일별 · 이 구조 그대로)
{
  "1": {"title": "월요일", "blocks": [{"start": "08:00", "end": "09:20", "content": "구체적인 할 일", "type": "영어"}]},
  "2": {"title": "화요일", "blocks": []},
  "3": {"title": "수요일", "blocks": []},
  "4": {"title": "목요일", "blocks": []},
  "5": {"title": "금요일", "blocks": []},
  "6": {"title": "토요일", "blocks": []},
  "7": {"title": "일요일", "blocks": []}
}

- 최상위 키 "1"~"7" = 요일 (1=월 2=화 3=수 4=목 5=금 6=토 7=일). **7개 요일 모두** 채워줘.
- 요일마다 blocks 를 다르게 (평일/주말·학원 등 반영). 각 요일 규칙은 아래와 동일.

$commonRules
""".trim()

    /** 붙여넣은 JSON 이 요일별 구조(키 "1".."7")인지 판별 */
    fun isWeekJson(json: String): Boolean = runCatching {
        val o = JSONObject(json)
        !o.has("blocks") && (1..7).any { o.has(it.toString()) }
    }.getOrDefault(false)

    /** 요일별 JSON 검증 후 저장(+요일별 모드 켬). 실패 시 Result.failure. */
    fun applyWeek(prefs: Prefs, json: String): Result<Unit> = runCatching {
        val o = JSONObject(json)
        val out = JSONObject()
        var any = false
        for (d in 1..7) {
            val k = d.toString()
            if (o.has(k)) {
                val dayJson = o.getJSONObject(k).toString()
                parse(dayJson).getOrThrow()          // 각 요일 검증
                out.put(k, JSONObject(dayJson))
                any = true
            }
        }
        if (!any) throw IllegalArgumentException("요일(1~7) 데이터가 없어요")
        prefs.timetableByDayJson = out.toString()
        prefs.timetablePerDay = true
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    fun isStudy(type: String) = type in STUDY
    fun isRest(type: String) = type in REST

    /** 현재 시각(분)이 속한 블록 index. 없으면 -1 */
    fun currentIndex(blocks: List<TimeBlock>, nowMin: Int): Int =
        blocks.indexOfFirst {
            (nowMin >= it.startMin && nowMin < it.endMin) ||
                (nowMin + 1440 >= it.startMin && nowMin + 1440 < it.endMin)   // 자정 넘긴 블록의 새벽 구간
        }

    /** 다음(아직 시작 안 한) 블록 index. 없으면 -1 */
    fun nextIndex(blocks: List<TimeBlock>, nowMin: Int): Int =
        blocks.indexOfFirst { it.startMin > nowMin }
}
