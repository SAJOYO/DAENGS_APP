package com.daengs.app.ui.notify

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.notify.DaengsNotice
import com.daengs.app.notify.NoticeInbox
import com.daengs.app.notify.RESULT_CHANNEL_ID
import com.daengs.app.notify.notificationSettingsIntent
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 종 아이콘이 여는 알림 목록.
 *
 * **시스템 알림 그림자의 사본이 아니다.** 그림자에서 한 번 밀어 낸 알림은 다시 볼 곳이
 * 없고, 알림 권한을 안 준 사람에게는 애초에 아무것도 안 뜬다. 여기에는 **앱이 띄우려 한
 * 것이 모두** 남는다 ([com.daengs.app.notify.postDaengsNotice]).
 *
 * 사용자가 짚어서 생겼다 — *"지금 벨 모양 누르면 알림 설정창으로 가는데, 여기에 알림이
 * 있으면 불이 들어오고 그 내용이 떠야하는거 아니야?"*. 종은 「설정」이 아니라 「알림
 * 목록」이라는 뜻이다.
 *
 * @param unreadIds 점을 그릴 줄. **화면을 여는 순간의 것으로 고정한다** — 여는 동시에
 *   전부 읽음이 되므로, 살아 있는 값으로 그리면 점이 뜨자마자 사라진다.
 */
@Composable
fun NoticeInboxScreen(
    notices: List<DaengsNotice>,
    onBack: () -> Unit,
    onOpen: (DaengsNotice) -> Unit,
    onClear: () -> Unit,
    unreadIds: Set<Int> = emptySet(),
    now: LocalDateTime = LocalDateTime.now(),
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(CreamBg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .semantics { contentDescription = "뒤로" },
                    contentAlignment = Alignment.Center,
                    // 뒤로 아이콘은 따로 없다. 오른쪽 꺾쇠를 돌려 쓴다 (`CareLogScreen` 과 같다).
                ) { DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(18.dp).rotate(180f), tint = TextDark) }
                Spacer(Modifier.size(4.dp))
                Text(
                    "알림",
                    color = TextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // 빈 목록에서는 지울 것이 없다. 눌리는 글자를 남겨 두면 눌러 보게 된다.
                if (notices.isNotEmpty()) DaengsTextAction("다 지우기", onClear, tint = TextMuted)
            }

            if (notices.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("아직 받은 알림이 없어요.", color = TextMuted, fontSize = 15.sp)
                        Spacer(Modifier.size(6.dp))
                        Text("분석이 끝나거나 카드가 완성되면 여기에 쌓여요.", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notices, key = { it.id }) { notice ->
                        NoticeRow(notice, now, unread = notice.id in unreadIds) { onOpen(notice) }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching { context.startActivity(notificationSettingsIntent(context)) }
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // **시스템 설정으로 가는 길은 남긴다.** 켜고 끄는 것은 안드로이드가 채널마다
                // 해 주고, 앱에 스위치를 또 두면 둘이 어긋날 수 있다
                // (`notify/DaengsNotifications.kt` 의 `notificationSettingsIntent`).
                Text("알림 설정", color = TextDark, fontSize = 15.sp, modifier = Modifier.weight(1f))
                DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(16.dp), tint = TextMuted)
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: DaengsNotice, now: LocalDateTime, unread: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(notice.title, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(3.dp))
            Text(notice.text, color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.size(5.dp))
            Text(noticeWhen(notice.atMillis, now), color = TextMuted, fontSize = 11.sp)
        }
        // 안 읽은 줄에만 점. 종에 들어온 불과 같은 뜻이다.
        if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(DaengsColors.BrandPrimary))
    }
}

/**
 * 언제 온 알림인가.
 *
 * 오늘 것은 시각만(`오후 8:12`), 어제 것은 「어제」, 그 앞은 날짜다. **오늘 온 알림에
 * 날짜를 붙이면** 목록이 같은 날짜로 도배된다.
 */
internal fun noticeWhen(
    atMillis: Long,
    now: LocalDateTime,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), zone)
    val today: LocalDate = now.toLocalDate()
    return when (at.toLocalDate()) {
        today -> at.format(TIME)
        today.minusDays(1) -> "어제 " + at.format(TIME)
        else -> at.format(DATE)
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

/**
 * `MainActivity` 가 쓰는 진입점.
 *
 * **열면 안 읽은 것이 사라진다** — 목록을 봤으면 본 것이다. 줄마다 따로 읽음 표시를 두면
 * 사용자가 관리해야 할 것이 하나 늘어난다. 점은 [unreadIds] 로 남겨 이번에 새로 온 것이
 * 무엇이었는지는 보이게 한다.
 */
@Composable
fun NoticeInboxRoute(onBack: () -> Unit, onOpen: (DaengsNotice) -> Unit) {
    val context = LocalContext.current
    val notices by NoticeInbox.notices.collectAsState()
    val unreadIds = remember { NoticeInbox.notices.value.filterNot { it.read }.map { it.id }.toSet() }
    LaunchedEffect(Unit) { NoticeInbox.markAllRead(context) }
    NoticeInboxScreen(
        notices = notices,
        onBack = onBack,
        onOpen = onOpen,
        onClear = { NoticeInbox.clear(context) },
        unreadIds = unreadIds,
    )
}

@Preview(widthDp = 411, heightDp = 640, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun NoticeInboxPreview() {
    DaengsTheme {
        NoticeInboxScreen(
            notices = listOf(
                DaengsNotice(1, RESULT_CHANNEL_ID, "포토 카드가 완성됐어요.", "도감에서 뒤집어 보세요.", 0L),
                DaengsNotice(2, RESULT_CHANNEL_ID, "산책 일기 장면이 준비됐어요.", "오늘 산책을 일기로 남겨 보세요.", 0L),
            ),
            onBack = {},
            onOpen = {},
            onClear = {},
            unreadIds = setOf(1),
            now = LocalDateTime.of(2026, 9, 19, 20, 30),
        )
    }
}

@Preview(widthDp = 411, heightDp = 640, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun NoticeInboxEmptyPreview() {
    DaengsTheme { NoticeInboxScreen(notices = emptyList(), onBack = {}, onOpen = {}, onClear = {}) }
}
