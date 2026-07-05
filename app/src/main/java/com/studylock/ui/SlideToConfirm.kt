package com.studylock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 끝까지 밀면 onConfirm 발동. 락 시작 같은 비가역 동작 확정용.
 */
@Composable
fun SlideToConfirm(
    text: String,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    val density = LocalDensity.current
    val trackHeight = 64.dp
    val thumb = 56.dp
    val edge = 4.dp
    val thumbPx = with(density) { thumb.toPx() }
    val edgePx = with(density) { edge.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }

    val maxDrag = (trackWidthPx - thumbPx - edgePx * 2).coerceAtLeast(0f)
    val animated by animateFloatAsState(targetValue = dragPx, label = "thumb")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .background(GrayField, RoundedCornerShape(32.dp))
            .border(1.dp, GrayLight, RoundedCornerShape(32.dp))
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Gray)

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((animated + edgePx).roundToInt(), 0) }
                .size(thumb)
                .background(Ink, CircleShape)
                .pointerInput(maxDrag) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!confirmed) {
                                if (dragPx >= maxDrag * 0.95f && maxDrag > 0f) {
                                    confirmed = true
                                    dragPx = maxDrag
                                    onConfirm()
                                } else {
                                    dragPx = 0f
                                }
                            }
                        }
                    ) { _, delta ->
                        if (!confirmed) {
                            dragPx = (dragPx + delta).coerceIn(0f, maxDrag)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Paper)
        }
    }
}
