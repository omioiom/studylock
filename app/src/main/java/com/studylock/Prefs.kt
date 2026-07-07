package com.studylock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 앱 상태 저장소. SharedPreferences 기반.
 * Device Owner 권한으로 락이 걸리면 사용자가 데이터를 임의로 못 지움.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("studylock", Context.MODE_PRIVATE)

    /** 셋업 완료 + 락 활성화 여부 */
    var locked: Boolean
        get() = sp.getBoolean(KEY_LOCKED, false)
        set(v) = sp.edit().putBoolean(KEY_LOCKED, v).apply()

    /** 본가(기기관리자판)는 모든 프리미엄 기능이 항상 열려 있음 */
    val isPremium: Boolean get() = true

    /** 목표일(수능) epoch millis (자정 기준) */
    var targetDateMillis: Long
        get() = sp.getLong(KEY_TARGET, 0L)
        set(v) = sp.edit().putLong(KEY_TARGET, v).apply()

    /** D-day 이름 (예: 수능, 중간고사) */
    var ddayLabel: String
        get() = sp.getString(KEY_DDAY_LABEL, null)?.takeIf { it.isNotBlank() } ?: "수능"
        set(v) = sp.edit().putString(KEY_DDAY_LABEL, v.trim()).apply()

    /** 허용 앱 패키지 집합 */
    var allowedPackages: Set<String>
        get() = HashSet(sp.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet(KEY_ALLOWED, v).apply()

    /** 옵션: 시계조작 차단 */
    var optBlockTime: Boolean
        get() = sp.getBoolean(KEY_OPT_TIME, true)
        set(v) = sp.edit().putBoolean(KEY_OPT_TIME, v).apply()

    /** 옵션: 안전모드 차단 */
    var optBlockSafeBoot: Boolean
        get() = sp.getBoolean(KEY_OPT_SAFEBOOT, true)
        set(v) = sp.edit().putBoolean(KEY_OPT_SAFEBOOT, v).apply()

    /** 옵션: 상태바(알림창) 차단 */
    var optBlockStatusBar: Boolean
        get() = sp.getBoolean(KEY_OPT_STATUSBAR, true)
        set(v) = sp.edit().putBoolean(KEY_OPT_STATUSBAR, v).apply()

    // ---- 시간표 ----

    /** 사용자가 저장한 시간표 JSON. null/빈값이면 기본 asset 사용 */
    var timetableJson: String?
        get() = sp.getString(KEY_TT_JSON, null)
        set(v) = sp.edit().putString(KEY_TT_JSON, v).apply()

    /** 시간표 블록 유형 목록 (사용자 편집 가능, 기본 6개) */
    var timetableTypes: List<String>
        get() = sp.getString(KEY_TT_TYPES, null)?.split("")?.filter { it.isNotBlank() }
            ?: listOf("국어", "영어", "수학", "탐구", "식사", "휴식")
        set(v) = sp.edit().putString(KEY_TT_TYPES, v.distinct().joinToString("")).apply()

    /** 요일별로 다른 시간표 사용 */
    var timetablePerDay: Boolean
        get() = sp.getBoolean(KEY_TT_PERDAY, false)
        set(v) = sp.edit().putBoolean(KEY_TT_PERDAY, v).apply()

    /** 요일별 시간표 JSON { "1":{title,blocks}, ... "7":{...} } (1=월 .. 7=일) */
    var timetableByDayJson: String?
        get() = sp.getString(KEY_TT_BYDAY, "{}")
        set(v) = sp.edit().putString(KEY_TT_BYDAY, v).apply()

    /** 보기 디자인 0:리스트 1:타임라인 2:카드 3:집중 4:한눈에 */
    var ttViewMode: Int
        get() = sp.getInt(KEY_TT_VIEW, 0)
        set(v) = sp.edit().putInt(KEY_TT_VIEW, v).apply()

    /** D-day 표시 0:끄기 1:작게 2:크게 */
    var ttDday: Int
        get() = sp.getInt(KEY_TT_DDAY, 1)
        set(v) = sp.edit().putInt(KEY_TT_DDAY, v).apply()

    /** 지난 일정 흐리게 */
    var ttDimPast: Boolean
        get() = sp.getBoolean(KEY_TT_DIMPAST, true)
        set(v) = sp.edit().putBoolean(KEY_TT_DIMPAST, v).apply()

    /** 현재 일정 자동 스크롤 */
    var ttAutoScroll: Boolean
        get() = sp.getBoolean(KEY_TT_AUTOSCROLL, true)
        set(v) = sp.edit().putBoolean(KEY_TT_AUTOSCROLL, v).apply()

    /** 홈(바탕) D-day 모드 0:크게 1:작게(자리에 '지금 할 일') */
    var homeDday: Int
        get() = sp.getInt(KEY_HOME_DDAY, 0)
        set(v) = sp.edit().putInt(KEY_HOME_DDAY, v).apply()

    /** 다크 모드 */
    var darkMode: Boolean
        get() = sp.getBoolean(KEY_DARK_MODE, false)
        set(v) = sp.edit().putBoolean(KEY_DARK_MODE, v).apply()

    // ---- TODO(할 일) ----

    /** 할 일 목록 JSON ({"todos":[...]}) */
    var todoJson: String?
        get() = sp.getString(KEY_TODO_JSON, null)
        set(v) = sp.edit().putString(KEY_TODO_JSON, v).apply()

    /** 기본 뷰 0=리스트 1=캘린더 2=타임라인 */
    var todoView: Int
        get() = sp.getInt(KEY_TODO_VIEW, 0)
        set(v) = sp.edit().putInt(KEY_TODO_VIEW, v).apply()

    /** 정렬 0=시간순 1=우선순위 2=카테고리 3=생성순 */
    var todoSort: Int
        get() = sp.getInt(KEY_TODO_SORT, 0)
        set(v) = sp.edit().putInt(KEY_TODO_SORT, v).apply()

    /** 리스트 그룹핑 0=날짜 1=상태 2=카테고리 */
    var todoGroup: Int
        get() = sp.getInt(KEY_TODO_GROUP, 0)
        set(v) = sp.edit().putInt(KEY_TODO_GROUP, v).apply()

    /** 완료 항목 표시 */
    var todoShowDone: Boolean
        get() = sp.getBoolean(KEY_TODO_SHOWDONE, true)
        set(v) = sp.edit().putBoolean(KEY_TODO_SHOWDONE, v).apply()

    /** 완료·지난 항목 흐리게 */
    var todoDimDone: Boolean
        get() = sp.getBoolean(KEY_TODO_DIMDONE, true)
        set(v) = sp.edit().putBoolean(KEY_TODO_DIMDONE, v).apply()

    /** 미완료 이월(지난 미완료를 오늘로) */
    var todoCarryOver: Boolean
        get() = sp.getBoolean(KEY_TODO_CARRY, true)
        set(v) = sp.edit().putBoolean(KEY_TODO_CARRY, v).apply()

    /** 주 시작 요일 0=월 1=일 */
    var todoWeekStart: Int
        get() = sp.getInt(KEY_TODO_WEEKSTART, 0)
        set(v) = sp.edit().putInt(KEY_TODO_WEEKSTART, v).apply()

    /** 다른 앱 쓸 때 진행 중인 일정 플로팅 표시 */
    var floatingSchedule: Boolean
        get() = sp.getBoolean(KEY_FLOAT_SCHED, false)
        set(v) = sp.edit().putBoolean(KEY_FLOAT_SCHED, v).apply()

    /** 일정 시작 알림 진동: 0=끄기 1=짧게 2=길게(3초3번) */
    var scheduleVibeMode: Int
        get() = sp.getInt(KEY_SCHED_VIBE, 1)
        set(v) = sp.edit().putInt(KEY_SCHED_VIBE, v).apply()

    /** 일정 시작 알림 */
    var ttNotifyStart: Boolean
        get() = sp.getBoolean(KEY_TT_NOTIFY_START, true)
        set(v) = sp.edit().putBoolean(KEY_TT_NOTIFY_START, v).apply()

    /** 일정 종료 알림 */
    var ttNotifyEnd: Boolean
        get() = sp.getBoolean(KEY_TT_NOTIFY_END, true)
        set(v) = sp.edit().putBoolean(KEY_TT_NOTIFY_END, v).apply()

    // ---- 집중 잠금 ----

    /** 집중 잠금 종료 시각(epoch millis). 0 이하면 잠금 아님 */
    var focusLockUntil: Long
        get() = sp.getLong(KEY_FOCUS_UNTIL, 0L)
        set(v) = sp.edit().putLong(KEY_FOCUS_UNTIL, v).apply()

    fun focusLockActive(): Boolean = focusLockUntil > System.currentTimeMillis()

    /** 임시(3분) 해제 종료 시각. 이 시각까지 키오스크를 풀어둔다. */
    var tempUnlockUntil: Long
        get() = sp.getLong(KEY_TEMP_UNTIL, 0L)
        set(v) = sp.edit().putLong(KEY_TEMP_UNTIL, v).apply()

    fun tempUnlockActive(): Boolean = tempUnlockUntil > System.currentTimeMillis()

    // ---- 스크린타임 ----

    /** 규칙 JSON (앱별/전체 제한 + 시간대 차단) */
    var screenTimeJson: String?
        get() = sp.getString(KEY_ST_RULES, null)
        set(v) = sp.edit().putString(KEY_ST_RULES, v).apply()

    /** 오늘 사용량 누적 기준 날짜 */
    var stUsageDate: String?
        get() = sp.getString(KEY_ST_DATE, null)
        set(v) = sp.edit().putString(KEY_ST_DATE, v).apply()

    /** 앱별 사용 초 JSON */
    var stUsageJson: String?
        get() = sp.getString(KEY_ST_USAGE, "{}")
        set(v) = sp.edit().putString(KEY_ST_USAGE, v).apply()

    /** 전체 사용 초 */
    var stTotalSeconds: Long
        get() = sp.getLong(KEY_ST_TOTAL, 0L)
        set(v) = sp.edit().putLong(KEY_ST_TOTAL, v).apply()

    /** 현재 사용 중 앱 패키지(세션) */
    var stSessionPkg: String?
        get() = sp.getString(KEY_ST_SESS_PKG, null)
        set(v) = sp.edit().putString(KEY_ST_SESS_PKG, v).apply()

    /** 세션 시작 시각 */
    var stSessionStart: Long
        get() = sp.getLong(KEY_ST_SESS_START, 0L)
        set(v) = sp.edit().putLong(KEY_ST_SESS_START, v).apply()

    /** 지난 날짜별 사용기록 JSON { "yyyy-mm-dd": {total, apps{}} } */
    var stHistoryJson: String?
        get() = sp.getString(KEY_ST_HIST, "{}")
        set(v) = sp.edit().putString(KEY_ST_HIST, v).apply()

    /** 예약 해제창에서 사용자가 수동 재잠금한 창(종료시각) — 그 창은 재해제 안 함 */
    var scheduledUnlockSkip: Long
        get() = sp.getLong(KEY_SCHED_SKIP, 0L)
        set(v) = sp.edit().putLong(KEY_SCHED_SKIP, v).apply()

    // ---- 화면 잠금 (자체 keyguard 대체: PIN/패턴) ----

    /** 0:없음 1:PIN 2:패턴 */
    var screenLockType: Int
        get() = sp.getInt(KEY_SL_TYPE, 0)
        set(v) = sp.edit().putInt(KEY_SL_TYPE, v).apply()

    /** 화면이 꺼졌다 켜지면 잠금 필요 표시(런타임) */
    var needsScreenUnlock: Boolean
        get() = sp.getBoolean(KEY_SL_NEED, false)
        set(v) = sp.edit().putBoolean(KEY_SL_NEED, v).apply()

    /** 마지막으로 연 앱(잠금 풀면 이 앱으로 복귀). 홈으로 돌아오면 비움 */
    var lastApp: String
        get() = sp.getString(KEY_LAST_APP, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST_APP, v).apply()

    fun screenLockEnabled() = screenLockType != 0

    fun setScreenLock(type: Int, value: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putInt(KEY_SL_TYPE, type)
            .putString(KEY_SL_SALT, salt.toHex())
            .putString(KEY_SL_HASH, hash(value, salt))
            .apply()
    }

    fun verifyScreenLock(value: String): Boolean {
        val saltHex = sp.getString(KEY_SL_SALT, null) ?: return false
        val expected = sp.getString(KEY_SL_HASH, null) ?: return false
        return hash(value, saltHex.fromHex()) == expected
    }

    fun clearScreenLock() {
        sp.edit().putInt(KEY_SL_TYPE, 0).remove(KEY_SL_SALT).remove(KEY_SL_HASH).apply()
    }

    // ---- PIN (salt + SHA-256 해시만 저장, 평문 저장 안 함) ----

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        sp.edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hash)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltHex = sp.getString(KEY_PIN_SALT, null) ?: return false
        val expected = sp.getString(KEY_PIN_HASH, null) ?: return false
        return hash(pin, saltHex.fromHex()) == expected
    }

    fun clearAll() = sp.edit().clear().apply()

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray()).toHex()
    }

    companion object {
        private const val KEY_LOCKED = "locked"
        private const val KEY_TARGET = "target"
        private const val KEY_DDAY_LABEL = "dday_label"
        private const val KEY_ALLOWED = "allowed"
        private const val KEY_OPT_TIME = "opt_time"
        private const val KEY_OPT_SAFEBOOT = "opt_safeboot"
        private const val KEY_OPT_STATUSBAR = "opt_statusbar"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_TT_JSON = "tt_json"
        private const val KEY_TT_TYPES = "tt_types"
        private const val KEY_TT_PERDAY = "tt_perday"
        private const val KEY_TT_BYDAY = "tt_byday"
        private const val KEY_TT_VIEW = "tt_view"
        private const val KEY_TT_DDAY = "tt_dday"
        private const val KEY_TT_DIMPAST = "tt_dimpast"
        private const val KEY_TT_AUTOSCROLL = "tt_autoscroll"
        private const val KEY_HOME_DDAY = "home_dday"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_TODO_JSON = "todo_json"
        private const val KEY_TODO_VIEW = "todo_view"
        private const val KEY_TODO_SORT = "todo_sort"
        private const val KEY_TODO_GROUP = "todo_group"
        private const val KEY_TODO_SHOWDONE = "todo_showdone"
        private const val KEY_TODO_DIMDONE = "todo_dimdone"
        private const val KEY_TODO_CARRY = "todo_carry"
        private const val KEY_TODO_WEEKSTART = "todo_weekstart"
        private const val KEY_FLOAT_SCHED = "floating_schedule"
        private const val KEY_SCHED_VIBE = "schedule_vibe_mode"
        private const val KEY_TT_NOTIFY_START = "tt_notify_start"
        private const val KEY_TT_NOTIFY_END = "tt_notify_end"
        private const val KEY_FOCUS_UNTIL = "focus_until"
        private const val KEY_TEMP_UNTIL = "temp_until"
        private const val KEY_ST_RULES = "st_rules"
        private const val KEY_ST_DATE = "st_date"
        private const val KEY_ST_USAGE = "st_usage"
        private const val KEY_ST_TOTAL = "st_total"
        private const val KEY_ST_SESS_PKG = "st_sess_pkg"
        private const val KEY_ST_SESS_START = "st_sess_start"
        private const val KEY_ST_HIST = "st_history"
        private const val KEY_SCHED_SKIP = "sched_skip"
        private const val KEY_SL_TYPE = "sl_type"
        private const val KEY_SL_SALT = "sl_salt"
        private const val KEY_SL_HASH = "sl_hash"
        private const val KEY_SL_NEED = "sl_need"
        private const val KEY_LAST_APP = "last_app"

        /** PIN 길이 (알파벳+숫자) */
        const val PIN_LEN = 10

        /** 혼동 문자(0,O,1,I,L) 제외한 대문자+숫자 알파벳 */
        private const val PIN_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        /** 10자리 알파벳+숫자 PIN 생성 (대문자) */
        fun generatePin(): String {
            val rnd = SecureRandom()
            return (0 until PIN_LEN).joinToString("") {
                PIN_ALPHABET[rnd.nextInt(PIN_ALPHABET.length)].toString()
            }
        }

        /** 입력 정규화: 대문자화 + 영숫자만 + 최대 길이 */
        fun normalizePinInput(raw: String): String =
            raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(PIN_LEN)
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }

private fun String.fromHex(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
