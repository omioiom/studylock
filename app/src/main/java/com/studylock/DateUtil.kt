package com.studylock

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object DateUtil {

    /** 기본 제안값: 2027학년도 수능 (2026-11-19) */
    val defaultTarget: LocalDate = LocalDate.of(2026, 11, 19)

    fun toEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** DatePicker(UTC 기준) 초기값용: 시스템존 날짜 → UTC 자정 millis */
    fun toUtcMillis(millis: Long): Long {
        val d = toLocalDate(millis)
        return d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 오늘부터 목표일까지 남은 일수 (당일 0, 지나면 음수) */
    fun daysUntil(targetMillis: Long): Long {
        val today = LocalDate.now(ZoneId.systemDefault())
        val target = toLocalDate(targetMillis)
        return ChronoUnit.DAYS.between(today, target)
    }

    /** 목표일 도달(또는 경과) 여부 */
    fun reached(targetMillis: Long): Boolean = daysUntil(targetMillis) <= 0

    fun format(millis: Long): String {
        val d = toLocalDate(millis)
        return "%04d.%02d.%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }
}
