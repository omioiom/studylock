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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.studylock.DateUtil
import com.studylock.GoogleGuardService
import com.studylock.Prefs
import com.studylock.TimetableLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 그리드용 앱 항목. 아이콘은 미리 비트맵으로 변환해 캐시. */
data class KioskApp(val packageName: String, val label: String, val icon: ImageBitmap)

/** 라벨·아이콘 로딩이 비싸서(앱당 수십 ms, 메인스레드 잭 유발) 프로세스 단위로 캐시 */
private val kioskAppCache = java.util.concurrent.ConcurrentHashMap<String, KioskApp>()

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
    onOpenTodo: () -> Unit,
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

    var apps by remember { mutableStateOf<List<KioskApp>>(emptyList()) }
    // nowMin 도 키에 넣어 차단(숨김)된 앱이 1분 내 그리드에서 사라지도록.
    // PackageManager 조회·아이콘 디코딩은 IO 스레드 + 캐시로 — 메인스레드 걸림 방지.
    LaunchedEffect(refreshKey, prefs.allowedPackages, nowMin) {
        val pkgs = prefs.allowedPackages
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pkgs.mapNotNull { pkg ->
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)  // 숨김 앱은 예외 → 그리드서 제외
                    kioskAppCache.getOrPut(pkg) {
                        KioskApp(pkg, pm.getApplicationLabel(ai).toString(),
                            pm.getApplicationIcon(ai).toBitmap(120, 120).asImageBitmap())
                    }
                }.getOrNull()
            }.sortedBy { it.label.lowercase() }
        }
    }

    // ---- 허용앱 롱프레스 메뉴 ----
    var menuApp by remember { mutableStateOf<KioskApp?>(null) }
    var slide by remember { mutableStateOf<Pair<String, KioskApp>?>(null) }
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

    // 접근성(구글 검색 차단 가드)이 꺼져 있으면 켜라고 안내 — 꺼진 동안엔 구글앱 전체가 막힘
    var guardOff by remember(refreshKey) { mutableStateOf(!GoogleGuardService.isEnabled(context)) }
    if (guardOff) {
        AccessibilityPromptDialog(
            onGoSettings = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                guardOff = false
            },
            onDismiss = { guardOff = false }
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

        // ---- TODO(할 일) 우상단 진입점 ----
        TodoIcon(onClick = onOpenTodo, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** 접근성 꺼짐 안내 모달 — 구글 검색 차단 가드를 켜라고 안내 + 설정으로 보내는 버튼 */
@Composable
private fun AccessibilityPromptDialog(onGoSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paper).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 방패 글리프
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Ink),
                contentAlignment = Alignment.Center
            ) { Text("!", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            Text("접근성 켜기가 필요해요", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "구글 앱의 ‘검색’ 화면 진입을 막으려면 접근성 권한이 필요해요.\n" +
                    "꺼져 있으면 Gemini와 검색을 구분할 수 없어, 지금은 구글 앱이 전부 막혀 있어요.\n" +
                    "켜면 Gemini는 그대로 쓰고 검색만 차단돼요.",
                color = Gray, fontSize = 13.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink, RoundedCornerShape(14.dp))
                    .clickable(onClick = onGoSettings).padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text("접근성 켜러 가기", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(6.dp))
            Text("나중에", color = Gray, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun TodoIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width; val h = size.height
            val stroke = h * 0.09f
            // 체크리스트: 3줄 + 각 줄 앞 체크박스
            val rows = listOf(0.2f, 0.5f, 0.8f)
            rows.forEach { yf ->
                val y = h * yf
                val boxS = h * 0.16f
                drawRect(Ink, topLeft = androidx.compose.ui.geometry.Offset(0f, y - boxS / 2),
                    size = androidx.compose.ui.geometry.Size(boxS, boxS),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 0.8f))
                drawLine(Ink,
                    androidx.compose.ui.geometry.Offset(w * 0.34f, y),
                    androidx.compose.ui.geometry.Offset(w, y), strokeWidth = stroke)
            }
        }
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
private fun AppTile(entry: KioskApp, onLongClick: () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(bitmap = entry.icon, contentDescription = entry.label, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            entry.label, color = Ink, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

