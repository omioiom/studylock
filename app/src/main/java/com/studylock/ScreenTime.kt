package com.studylock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 앱별/전체 스크린타임 + 시간대 차단.
 * - device-owner 의 setApplicationHidden 으로 차단(앱이 사라지고 실행 불가).
 * - 사용시간은 우리(런처)가 앱 실행~홈 복귀 사이를 측정해 누적(권한 불필요).
 * - 규칙은 생성 후 1시간 안에만 수정/삭제 가능(그 뒤엔 잠금).
 */

const val EDIT_WINDOW_MS = 60 * 60 * 1000L  // 1시간

// days = 요일 비트마스크. 0 = 매일(모든 요일). bit0=월 … bit6=일 (dow 1~7).
data class AppLimit(val pkg: String, val limitMin: Int, val createdAt: Long, val days: Int = 0)
data class BlockWindow(
    val id: Long, val apps: List<String>,
    val startMin: Int, val endMin: Int, val createdAt: Long,
    val label: String = "",   // 일정별 차단이면 일정 이름, 직접 추가면 ""
    val blockId: String = "",  // 연결된 시간표 블록 id (있으면 이름·시간 바껴도 이름 추적)
    val days: Int = 0
)
/** 화이트리스트: 특정 앱을 이 시간대에만 허용(그 외엔 차단) */
data class AllowWindow(val id: Long, val pkg: String, val startMin: Int, val endMin: Int, val createdAt: Long, val days: Int = 0)

/** 화이트리스트: 이 시간대엔 스터디락 전체 해제 */
data class UnlockWindow(val id: Long, val startMin: Int, val endMin: Int, val createdAt: Long, val days: Int = 0)

data class ScreenTimeRules(
    val totalLimitMin: Int,
    val totalCreatedAt: Long,
    val appLimits: List<AppLimit>,
    val windows: List<BlockWindow>,
    val allowWindows: List<AllowWindow> = emptyList(),
    val unlockWindows: List<UnlockWindow> = emptyList(),
    val totalDays: Int = 0
) {
    fun isEmpty() = totalLimitMin <= 0 && appLimits.isEmpty() && windows.isEmpty() &&
        allowWindows.isEmpty() && unlockWindows.isEmpty()
}

object ScreenTime {

    fun editable(createdAt: Long, now: Long) = now - createdAt < EDIT_WINDOW_MS

    /** 오늘 요일 1=월 … 7=일 */
    fun dowOf(nowMs: Long): Int =
        java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().dayOfWeek.value

    /** days 비트마스크(0=매일)가 dow(1~7)에 적용되나 */
    fun appliesOn(days: Int, dow: Int): Boolean = days == 0 || (days and (1 shl (dow - 1))) != 0

    /** 비트마스크 → "매일" / "월·수·금" 라벨 */
    fun daysLabel(days: Int): String {
        if (days == 0 || days == 0b1111111) return "매일"
        val names = listOf("월", "화", "수", "목", "금", "토", "일")
        return (1..7).filter { (days and (1 shl (it - 1))) != 0 }.joinToString("·") { names[it - 1] }
    }

    /** nowMin 이 [startMin, endMin) 구간인지. endMin<=startMin 이면 자정 넘김으로 처리. */
    fun inWindow(startMin: Int, endMin: Int, nowMin: Int): Boolean = when {
        startMin == endMin -> false                                  // 0길이 창 = 해당 없음(종일참 방지)
        endMin > startMin -> nowMin >= startMin && nowMin < endMin
        else -> nowMin >= startMin || nowMin < endMin                // 자정 넘김
    }

    // ---------- 규칙 직렬화 ----------

    fun parseRules(json: String?): ScreenTimeRules {
        if (json.isNullOrBlank()) return ScreenTimeRules(0, 0, emptyList(), emptyList())
        val o = runCatching { JSONObject(json) }.getOrNull()
            ?: return ScreenTimeRules(0, 0, emptyList(), emptyList())
        // 항목 하나가 깨져도 그 항목만 버리고 나머지 규칙은 유지 (차단이 통째로 꺼지는 fail-open 방지)
        fun <T> each(name: String, f: (JSONObject) -> T): List<T> {
            val arr = o.optJSONArray(name) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching { f(arr.getJSONObject(i)) }.getOrNull()
            }
        }
        val limits = each("appLimits") {
            AppLimit(it.getString("pkg"), it.getInt("limitMin"), it.optLong("createdAt", 0), it.optInt("days", 0))
        }
        val wins = each("windows") { b ->
            val apps = b.getJSONArray("apps").let { a -> (0 until a.length()).map { a.getString(it) } }
            BlockWindow(b.optLong("id", 0), apps, b.getInt("startMin"), b.getInt("endMin"),
                b.optLong("createdAt", 0), b.optString("label", ""), b.optString("blockId", ""), b.optInt("days", 0))
        }
        val allows = each("allowWindows") {
            AllowWindow(it.optLong("id", 0), it.getString("pkg"), it.getInt("startMin"), it.getInt("endMin"), it.optLong("createdAt", 0), it.optInt("days", 0))
        }
        val unlocks = each("unlockWindows") {
            UnlockWindow(it.optLong("id", 0), it.getInt("startMin"), it.getInt("endMin"), it.optLong("createdAt", 0), it.optInt("days", 0))
        }
        return ScreenTimeRules(o.optInt("totalLimitMin", 0), o.optLong("totalCreatedAt", 0), limits, wins, allows, unlocks, o.optInt("totalDays", 0))
    }

    fun serialize(r: ScreenTimeRules): String {
        val o = JSONObject()
        o.put("totalLimitMin", r.totalLimitMin)
        o.put("totalCreatedAt", r.totalCreatedAt)
        o.put("totalDays", r.totalDays)
        o.put("appLimits", JSONArray().apply {
            r.appLimits.forEach { put(JSONObject().put("pkg", it.pkg).put("limitMin", it.limitMin).put("createdAt", it.createdAt).put("days", it.days)) }
        })
        o.put("windows", JSONArray().apply {
            r.windows.forEach { w ->
                put(JSONObject()
                    .put("id", w.id).put("startMin", w.startMin).put("endMin", w.endMin)
                    .put("createdAt", w.createdAt).put("label", w.label).put("blockId", w.blockId).put("days", w.days)
                    .put("apps", JSONArray().apply { w.apps.forEach { put(it) } }))
            }
        })
        o.put("allowWindows", JSONArray().apply {
            r.allowWindows.forEach {
                put(JSONObject().put("id", it.id).put("pkg", it.pkg)
                    .put("startMin", it.startMin).put("endMin", it.endMin).put("createdAt", it.createdAt).put("days", it.days))
            }
        })
        o.put("unlockWindows", JSONArray().apply {
            r.unlockWindows.forEach {
                put(JSONObject().put("id", it.id)
                    .put("startMin", it.startMin).put("endMin", it.endMin).put("createdAt", it.createdAt).put("days", it.days))
            }
        })
        return o.toString()
    }

    // ---------- 날짜/사용량 ----------

    private fun dayKey(nowMs: Long): String =
        java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString()

    /** 날짜 바뀌면 어제 기록 보관 후 초기화 */
    fun ensureToday(prefs: Prefs, nowMs: Long) {
        val today = dayKey(nowMs)
        if (prefs.stUsageDate != today) {
            val prev = prefs.stUsageDate
            if (prev != null && prefs.stTotalSeconds > 0) archive(prefs, prev)
            prefs.stUsageDate = today
            prefs.stUsageJson = "{}"
            prefs.stTotalSeconds = 0
        }
    }

    private const val HISTORY_DAYS = 30

    private fun archive(prefs: Prefs, date: String) {
        runCatching {
            val hist = JSONObject(prefs.stHistoryJson ?: "{}")
            hist.put(date, JSONObject()
                .put("total", prefs.stTotalSeconds)
                .put("apps", JSONObject(prefs.stUsageJson ?: "{}")))
            val keys = hist.keys().asSequence().toList().sorted()
            if (keys.size > HISTORY_DAYS) keys.take(keys.size - HISTORY_DAYS).forEach { hist.remove(it) }
            prefs.stHistoryJson = hist.toString()
        }
    }

    data class DayUsage(val date: String, val totalSec: Long, val apps: Map<String, Long>)

    /** 오늘 사용량 (진행값) */
    fun todayUsage(prefs: Prefs, nowMs: Long): DayUsage {
        ensureToday(prefs, nowMs)
        return DayUsage(dayKey(nowMs), prefs.stTotalSeconds, usageMap(prefs))
    }

    /** 지난 날짜 기록(최신순) */
    fun history(prefs: Prefs): List<DayUsage> {
        val out = mutableListOf<DayUsage>()
        runCatching {
            val hist = JSONObject(prefs.stHistoryJson ?: "{}")
            hist.keys().forEach { d ->
                val e = hist.getJSONObject(d)
                val apps = mutableMapOf<String, Long>()
                val a = e.optJSONObject("apps") ?: JSONObject()
                a.keys().forEach { apps[it] = a.getLong(it) }
                out.add(DayUsage(d, e.optLong("total", 0), apps))
            }
        }
        return out.sortedByDescending { it.date }
    }

    private fun usageMap(prefs: Prefs): MutableMap<String, Long> {
        val m = mutableMapOf<String, Long>()
        runCatching {
            val o = JSONObject(prefs.stUsageJson ?: "{}")
            o.keys().forEach { m[it] = o.getLong(it) }
        }
        return m
    }

    private fun saveUsage(prefs: Prefs, m: Map<String, Long>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        prefs.stUsageJson = o.toString()
    }

    fun usedSeconds(prefs: Prefs, pkg: String): Long = usageMap(prefs)[pkg] ?: 0L
    fun totalUsedSeconds(prefs: Prefs): Long = prefs.stTotalSeconds

    /** 한 번 누적 최대치(초). 화면 꺼짐/Doze 로 틱이 한참 안 와도 폭주 방지 */
    private const val MAX_ACCRUE_SEC = 30 * 60L

    /** 진행 중 세션 누적. clear=true 면 세션 종료, false 면 시작시각만 갱신(틱). */
    fun accrue(prefs: Prefs, nowMs: Long, clear: Boolean) {
        val pkg = prefs.stSessionPkg
        if (pkg == null) return
        ensureToday(prefs, nowMs)
        val elapsed = ((nowMs - prefs.stSessionStart) / 1000).coerceIn(0, MAX_ACCRUE_SEC)
        if (elapsed > 0) {
            val m = usageMap(prefs)
            m[pkg] = (m[pkg] ?: 0L) + elapsed
            saveUsage(prefs, m)
            prefs.stTotalSeconds = prefs.stTotalSeconds + elapsed
        }
        if (clear) prefs.stSessionPkg = null else prefs.stSessionStart = nowMs
    }

    /** 새 앱 세션 시작(이전 세션 정산) */
    fun startSession(prefs: Prefs, pkg: String, nowMs: Long) {
        accrue(prefs, nowMs, clear = true)
        ensureToday(prefs, nowMs)
        prefs.stSessionPkg = pkg
        prefs.stSessionStart = nowMs
    }

    // ---------- 차단 판정 ----------

    /** 지금 숨겨야 할 앱 집합 */
    fun blockedApps(prefs: Prefs, nowMs: Long, nowMin: Int): Set<String> {
        ensureToday(prefs, nowMs)
        val r = parseRules(prefs.screenTimeJson)
        if (r.isEmpty()) return emptySet()
        val dow = dowOf(nowMs)
        // 요일별 모드가 꺼져 있으면 저장된 요일과 무관하게 매일 적용
        // (요일 선택 UI 가 안 보이는 상태에서 만든 규칙에 생성 요일이 박혀 있어도 무시)
        val perDay = prefs.timetablePerDay
        fun onToday(days: Int) = !perDay || appliesOn(days, dow)
        val blocked = mutableSetOf<String>()
        // 시간대 차단 (오늘 요일에 적용되는 것만)
        r.windows.forEach { w ->
            if (onToday(w.days) && inWindow(w.startMin, w.endMin, nowMin)) blocked += w.apps
        }
        // 앱별 제한
        val um = usageMap(prefs)
        r.appLimits.forEach { a ->
            if (onToday(a.days) && (um[a.pkg] ?: 0L) >= a.limitMin * 60L) blocked += a.pkg
        }
        // 전체 제한 → 모든 허용앱 차단
        if (r.totalLimitMin > 0 && onToday(r.totalDays) && prefs.stTotalSeconds >= r.totalLimitMin * 60L) {
            blocked += prefs.allowedPackages
        }
        // 화이트리스트(앱 허용창): 오늘 적용되는 허용창이 있는 앱은 그 시간 외엔 차단
        val todaysAllows = r.allowWindows.filter { onToday(it.days) }
        todaysAllows.map { it.pkg }.toSet().forEach { pkg ->
            val allowedNow = todaysAllows.any { it.pkg == pkg && inWindow(it.startMin, it.endMin, nowMin) }
            if (!allowedNow) blocked += pkg else blocked.remove(pkg)
        }
        return blocked
    }

    /** 지금이 '스터디락 전체 해제' 창 안이면 그 창 반환 (오늘 요일 적용분만) */
    fun activeUnlockWindow(prefs: Prefs, nowMin: Int): UnlockWindow? {
        val dow = dowOf(System.currentTimeMillis())
        return parseRules(prefs.screenTimeJson).unlockWindows
            .firstOrNull {
                (!prefs.timetablePerDay || appliesOn(it.days, dow)) &&
                    inWindow(it.startMin, it.endMin, nowMin)
            }
    }

    fun hasRules(prefs: Prefs): Boolean = !parseRules(prefs.screenTimeJson).isEmpty()
}
