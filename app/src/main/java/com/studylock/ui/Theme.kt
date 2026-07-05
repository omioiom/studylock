package com.studylock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 전역 다크모드 스위치. Compose 스냅샷 상태라, 아래 색 getter 들을 읽는 모든
 * 컴포저블/Canvas 가 토글 시 자동으로 다시 그려진다. (앱 시작 시 Prefs 로 초기화)
 */
object AppTheme {
    var dark by mutableStateOf(false)
}

// 라이트/다크 원색
private val LightInk = Color(0xFF000000)
private val LightPaper = Color(0xFFFFFFFF)
private val LightLine = Color(0xFFE6E6E6)
private val LightField = Color(0xFFF4F4F4)
private val DarkInk = Color(0xFFF3F3F3)
private val DarkPaper = Color(0xFF0B0B0B)
private val DarkLine = Color(0xFF2E2E2E)
private val DarkField = Color(0xFF1B1B1B)
private val MidGray = Color(0xFF8A8A8A)   // 양쪽 공용(흑/백 위 모두 판독 가능)

// 흑백 팔레트 — 다크모드에 따라 동적으로 반전
val Ink: Color get() = if (AppTheme.dark) DarkInk else LightInk
val Paper: Color get() = if (AppTheme.dark) DarkPaper else LightPaper
val Gray: Color get() = MidGray
val GrayLight: Color get() = if (AppTheme.dark) DarkLine else LightLine
val GrayField: Color get() = if (AppTheme.dark) DarkField else LightField

private val LightScheme = lightColorScheme(
    primary = LightInk, onPrimary = LightPaper,
    background = LightPaper, onBackground = LightInk,
    surface = LightPaper, onSurface = LightInk,
    surfaceVariant = LightField, onSurfaceVariant = MidGray,
    outline = LightLine, error = LightInk,
)

private val DarkScheme = darkColorScheme(
    primary = DarkInk, onPrimary = DarkPaper,
    background = DarkPaper, onBackground = DarkInk,
    surface = DarkPaper, onSurface = DarkInk,
    surfaceVariant = DarkField, onSurfaceVariant = MidGray,
    outline = DarkLine, error = DarkInk,
)

private val BwType = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = MidGray, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
)

@Composable
fun StudyLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (AppTheme.dark) DarkScheme else LightScheme,
        typography = BwType,
        content = content,
    )
}
