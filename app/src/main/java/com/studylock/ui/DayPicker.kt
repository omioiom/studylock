package com.studylock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 새 규칙 기본 선택 = 오늘 요일만 */
fun todayDaySet(): Set<Int> = setOf(com.studylock.ScreenTime.dowOf(System.currentTimeMillis()))

/** dow set(1~7) → 저장용 비트마스크. 7개(=매일)면 0. */
fun daysMask(sel: Set<Int>): Int =
    if (sel.size >= 7) 0 else sel.fold(0) { a, d -> a or (1 shl (d - 1)) }

/** 요일별 모드가 꺼져 있으면(DayPicker 미노출) 무조건 매일(0)로 저장 */
fun daysMaskFor(prefs: com.studylock.Prefs, sel: Set<Int>): Int =
    if (prefs.timetablePerDay) daysMask(sel) else 0

/** 비트마스크 → dow set(1~7). 0(매일)이면 전체. */
fun maskToDays(mask: Int): Set<Int> =
    if (mask == 0) (1..7).toSet() else (1..7).filter { (mask and (1 shl (it - 1))) != 0 }.toSet()

/**
 * 요일 선택기. selected = 활성 요일 dow set(1~7). 7개면 '매일'.
 * 요일별 시간표가 켜져 있을 때만 노출한다.
 */
@Composable
fun DayPicker(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val names = listOf("월", "화", "수", "목", "금", "토", "일")
    Column(Modifier.fillMaxWidth()) {
        DayChip("매일", selected.size >= 7, Modifier.fillMaxWidth()) { onChange((1..7).toSet()) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d ->
                DayChip(names[d - 1], d in selected && selected.size < 7, Modifier.weight(1f)) {
                    onChange(if (d in selected) selected - d else selected + d)
                }
            }
        }
    }
}

@Composable
private fun DayChip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) Ink else Paper, RoundedCornerShape(11.dp))
            .border(1.dp, if (on) Ink else GrayLight, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (on) Paper else Ink, fontSize = 13.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
