package com.studylock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.studylock.AppEntry
import com.studylock.DateUtil
import com.studylock.Prefs
import com.studylock.TimetableLoader
import kotlinx.coroutines.delay

/**
 * 키오스크 홈. 큰 D-day + 허용앱 그리드.
 * 우상단 모서리 5번 탭 = 숨김 PIN 진입.
 */
@Composable
fun KioskScreen(
    prefs: Prefs,
    refreshKey: Int,
    onLaunchApp: (String) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenFocusLock: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenSettings: () -> Unit,
    onExcludeApp: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onLimitApp: (String) -> Unit
) {
    val context = LocalContext.current
    val days = remember(refreshKey) { DateUtil.daysUntil(prefs.targetDateMillis) }
    val targetStr = remember(refreshKey) { DateUtil.format(prefs.targetDateMillis) }
    val smallDday = prefs.homeDday == 1

    // 시간표(현재 할 일) — 작게 모드일 때만 필요하지만 항상 로드해도 가벼움
    val timetable = remember(refreshKey) {
        TimetableLoader.parse(TimetableLoader.activeJson(context, prefs)).getOrNull()
    }
    var nowMin by remember { mutableIntStateOf(curMinutes()) }
    LaunchedEffect(Unit) { while (true) { nowMin = curMinutes(); delay(30_000) } }
    val nowBlock = timetable?.blocks?.let { bs ->
        TimetableLoader.currentIndex(bs, nowMin).takeIf { it >= 0 }?.let { bs[it] }
    }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    // nowMin 도 키에 넣어 차단(숨김)된 앱이 1분 내 그리드에서 사라지도록
    LaunchedEffect(refreshKey, prefs.allowedPackages, nowMin) {
        val pm = context.packageManager
        apps = prefs.allowedPackages.mapNotNull { pkg ->
            runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)  // 숨김 앱은 예외 → 그리드서 제외
                AppEntry(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    // ---- 허용앱 롱프레스 메뉴 ----
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var slide by remember { mutableStateOf<Pair<String, AppEntry>?>(null) }
    menuApp?.let { e ->
        ActionSheet(
            title = e.label,
            actions = listOf(
                "스터디락에서 제외" to { slide = "exclude" to e },
                "앱 삭제(제거)" to { slide = "uninstall" to e },
                "이 앱 설정" to {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${e.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }; Unit
                },
                "선택한 앱 하루 제한" to { onLimitApp(e.packageName) }
            ),
            onDismiss = { menuApp = null }
        )
    }
    slide?.let { (action, e) ->
        SlideConfirmDialog(
            title = if (action == "exclude") "스터디락에서 제외" else "앱 삭제",
            message = if (action == "exclude") "'${e.label}' 를 허용앱에서 빼요. (앱은 폰에 남아있어요)"
            else "'${e.label}' 를 폰에서 완전히 삭제해요.",
            onConfirm = {
                if (action == "exclude") onExcludeApp(e.packageName) else onUninstallApp(e.packageName)
                slide = null; menuApp = null
            },
            onDismiss = { slide = null }
        )
    }

    val ddayText = if (days >= 0) "D-$days" else "D+${-days}"

    Box(Modifier.fillMaxSize().background(Paper)) {

        Column(Modifier.fillMaxSize()) {

            // ---- 헤더 ----
            if (smallDday) {
                // D-day 작게 + 지금 할 일 크게
                Column(
                    Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 24.dp, start = 28.dp, end = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${prefs.ddayLabel} $ddayText", color = Gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(18.dp))

                    val blocks = remember(timetable) { timetable?.blocks?.sortedBy { it.startMin } ?: emptyList() }
                    val curIdx = TimetableLoader.currentIndex(blocks, nowMin)
                    val baseIdx = if (curIdx >= 0) curIdx else TimetableLoader.nextIndex(blocks, nowMin)
                    var blockOffset by remember { mutableIntStateOf(0) }
                    LaunchedEffect(refreshKey) { blockOffset = 0 }   // 다른 화면 갔다오면 현재로

                    if (blocks.isEmpty() || baseIdx < 0) {
                        Text("지금 할 일", color = Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("잡힌 일정 없음", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    } else {
                        val dispIdx = (baseIdx + blockOffset).coerceIn(0, blocks.size - 1)
                        val b = blocks[dispIdx]
                        val header = when {
                            dispIdx == curIdx -> "지금 할 일"
                            dispIdx < baseIdx -> "지난 일정"
                            dispIdx == baseIdx && curIdx < 0 -> "다음 일정"
                            else -> "예정"
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("‹", color = if (dispIdx > 0) Ink else GrayLight, fontSize = 34.sp,
                                modifier = Modifier.clickable(enabled = dispIdx > 0) { blockOffset-- }.padding(horizontal = 8.dp, vertical = 8.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(header, color = Gray, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(b.content, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                    lineHeight = 34.sp, textAlign = TextAlign.Center, maxLines = 3)
                                Spacer(Modifier.height(8.dp))
                                Text("${b.start}–${b.end}", color = Gray, fontSize = 13.sp)
                            }
                            Text("›", color = if (dispIdx < blocks.size - 1) Ink else GrayLight, fontSize = 34.sp,
                                modifier = Modifier.clickable(enabled = dispIdx < blocks.size - 1) { blockOffset++ }.padding(horizontal = 8.dp, vertical = 8.dp))
                        }
                    }
                }
            } else {
                // D-day 크게 (기본)
                Column(
                    Modifier.fillMaxWidth().padding(top = 72.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${prefs.ddayLabel}까지", style = MaterialTheme.typography.bodyMedium, color = Gray)
                    Spacer(Modifier.height(6.dp))
                    Text(ddayText, fontSize = 88.sp, fontWeight = FontWeight.Bold,
                        color = Ink, letterSpacing = (-3).sp)
                    Spacer(Modifier.height(6.dp))
                    Text(targetStr, color = Gray, fontSize = 14.sp)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(GrayLight))

            // ---- 그리드 (시간표 · 집중잠금 · 허용앱) ----
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item(key = "__timetable__") {
                    GlyphTile("시간표", onClick = onOpenTimetable) { TimetableGlyph() }
                }
                item(key = "__focuslock__") {
                    GlyphTile("집중잠금", onClick = onOpenFocusLock) { LockGlyph() }
                }
                item(key = "__screentime__") {
                    GlyphTile("스크린타임", onClick = onOpenScreenTime) { GaugeGlyph() }
                }
                item(key = "__whitelist__") {
                    GlyphTile("화이트리스트", onClick = onOpenWhitelist) { WhitelistGlyph() }
                }
                items(apps, key = { it.packageName }) { e ->
                    AppTile(e, onLongClick = { menuApp = e }) { onLaunchApp(e.packageName) }
                }
            }
        }

        // ---- 설정(기어) 좌상단 → 관리 메뉴는 설정 안에서 진입 ----
        GearIcon(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun GearIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width; val h = size.height
            val stroke = h * 0.07f
            val knobR = h * 0.11f
            val rows = listOf(0.22f to 0.34f, 0.5f to 0.68f, 0.78f to 0.30f)  // y, knobX
            rows.forEach { (yf, kx) ->
                val y = h * yf
                drawLine(Ink,
                    androidx.compose.ui.geometry.Offset(0f, y),
                    androidx.compose.ui.geometry.Offset(w, y), strokeWidth = stroke)
                drawCircle(Ink, radius = knobR,
                    center = androidx.compose.ui.geometry.Offset(w * kx, y))
                drawCircle(Paper, radius = knobR * 0.45f,
                    center = androidx.compose.ui.geometry.Offset(w * kx, y))
            }
        }
    }
}

private fun curMinutes(): Int {
    val t = java.time.LocalTime.now()
    return t.hour * 60 + t.minute
}

@Composable
private fun GlyphTile(label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).background(Ink, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { glyph() }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Ink, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2,
            textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
    }
}

/** 리스트 모양 (3줄) */
@Composable
private fun TimetableGlyph() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) {
            Box(Modifier.size(width = 24.dp, height = 3.dp).background(Paper, RoundedCornerShape(2.dp)))
        }
    }
}

/** 모래시계 모양 (스크린타임) */
@Composable
private fun GaugeGlyph() {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(width = 26.dp, height = 4.dp).background(Paper, RoundedCornerShape(2.dp)))
        Box(Modifier.size(width = 16.dp, height = 18.dp)
            .border(2.5.dp, Paper, RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 2.dp, bottomEnd = 2.dp)))
        Box(Modifier.size(width = 26.dp, height = 4.dp).background(Paper, RoundedCornerShape(2.dp)))
    }
}

/** 체크 목록 모양 (화이트리스트) */
@Composable
private fun WhitelistGlyph() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).background(Paper, RoundedCornerShape(2.dp)))
                Box(Modifier.size(width = 18.dp, height = 3.dp).background(Paper, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** 자물쇠 모양 (고리 + 몸통) */
@Composable
private fun LockGlyph() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 고리
        Box(Modifier.size(width = 16.dp, height = 12.dp)
            .border(2.5.dp, Paper, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
        // 몸통
        Box(Modifier.size(width = 24.dp, height = 18.dp).background(Paper, RoundedCornerShape(3.dp)))
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun AppTile(entry: AppEntry, onLongClick: () -> Unit, onClick: () -> Unit) {
    val bmp = remember(entry.packageName) { entry.icon.toBitmap(120, 120).asImageBitmap() }
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(bitmap = bmp, contentDescription = entry.label, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            entry.label, color = Ink, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

