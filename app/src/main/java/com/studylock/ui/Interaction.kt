package com.studylock.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 누르면 1회 실행 + 길게 누르고 있으면 자동 반복(따다닥). 시간 +/- 스테퍼용.
 * onStep 은 rememberUpdatedState 로 최신값을 읽으므로 반복 중에도 현재 값 기준으로 증감된다.
 */
fun Modifier.repeatingClickable(
    onStep: () -> Unit,
    initialDelayMs: Long = 350,   // 이만큼 누르고 있어야 반복 시작
    startIntervalMs: Long = 200,  // 반복 시작 간격(느림)
    minIntervalMs: Long = 26,     // 최고 속도 간격(빠름)
    accel: Float = 0.80f,         // 매 스텝마다 간격 축소 → 가속
): Modifier = composed {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            val job = scope.launch {
                step()                       // 누르자마자 1회
                delay(initialDelayMs)
                var interval = startIntervalMs
                while (isActive) {
                    step()
                    delay(interval)
                    interval = (interval * accel).toLong().coerceAtLeast(minIntervalMs)  // 점점 빨라짐
                }
            }
            waitForUpOrCancellation()
            job.cancel()
        }
    }
}
