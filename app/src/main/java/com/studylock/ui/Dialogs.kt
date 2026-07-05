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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** 블랙&화이트 모던 확인 다이얼로그 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String? = null,
    confirmLabel: String = "확인",
    cancelLabel: String = "취소",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(22.dp)).padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Gray)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).border(1.dp, GrayLight, RoundedCornerShape(13.dp))
                        .clickable(onClick = onDismiss).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text(cancelLabel, color = Ink, style = MaterialTheme.typography.labelLarge) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(Ink, RoundedCornerShape(13.dp))
                        .clickable { onConfirm(); onDismiss() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text(confirmLabel, color = Paper, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** 슬라이드로 확인하는 다이얼로그(제외·삭제처럼 신중해야 할 동작) */
@Composable
fun SlideConfirmDialog(
    title: String,
    message: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(22.dp)).padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Gray)
            }
            Spacer(Modifier.height(20.dp))
            SlideToConfirm(text = "밀어서 확인") { onConfirm() }
            Spacer(Modifier.height(6.dp))
            Text(
                "취소", style = MaterialTheme.typography.bodyLarge, color = Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(12.dp)
            )
        }
    }
}

/** 블랙&화이트 액션 시트(하단 목록 메뉴). 항목 클릭 시 실행. */
@Composable
fun ActionSheet(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(22.dp)).padding(vertical = 18.dp)
        ) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, color = Ink,
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(6.dp))
            actions.forEach { (label, action) ->
                Text(
                    label, style = MaterialTheme.typography.bodyLarge, color = Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { action(); onDismiss() }
                        .padding(vertical = 16.dp)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 24.dp).background(GrayLight))
            Text(
                "닫기", style = MaterialTheme.typography.bodyLarge, color = Gray,
                textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 16.dp)
            )
        }
    }
}
