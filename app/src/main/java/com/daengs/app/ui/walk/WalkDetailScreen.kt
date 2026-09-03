package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.pet.Pet
import com.daengs.app.ui.common.DaengsFloatingButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.WalkSummary

/**
 * 산책 하나.
 *
 * 지도는 산책 화면과 같은 [MapHost] 를 쓰되 **내 위치도 장소 마커도 없다.** 지난
 * 기록이라 "지금 어디"는 뜻이 없고, 남길 것은 그날 지나온 길뿐이다.
 *
 * 경로는 세그먼트마다 따로 그린다 — 일시정지나 GPS 점프 앞뒤를 한 선으로 이으면
 * 걷지 않은 길이 지도에 그려진다.
 */
@Composable
fun WalkDetailScreen(
    sessionId: String,
    history: WalkHistory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 이름을 붙이는 데 쓴다. 기록에는 id 만 있다. */
    pets: List<Pet> = emptyList(),
) {
    val inspectionMode = LocalInspectionMode.current
    var detail by remember(sessionId) { mutableStateOf<WalkSessionDetail?>(null) }

    LaunchedEffect(sessionId, history) {
        detail = history.sessionDetail(sessionId)
    }

    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(PinkFaint)) {
        val walk = detail?.summary
        val route = detail?.route
        val completedRoute = route?.toCompletedRouteLayerState(formatTime = ::formatWalkClock)
            ?: CompletedRouteLayerState()
        if (!inspectionMode) {
            MapHost(
                scene = MapScene(completedRoute = completedRoute),
                searchOrigin = null,
                followDevice = false,
                onCameraIdle = {},
                onCameraGesture = {},
                onSelectPlace = {},
                // 경로 전체가 한눈에 들어오게 맞춘다. 첫 좌표로 가는 것과는 다르다 —
                // 한 시간 걸은 산책은 첫 좌표만 보면 어디를 돌았는지 알 수 없다.
                //
                // 그릴 선이 없으면 **그 산책이 있었던 자리**로 간다. 안 그러면 지도가
                // 네이버 기본 카메라(서울시청)에 앉아, 강남에서 한 산책이 시청에서 한
                // 것처럼 보인다.
                fitBounds = route?.bounds.orEmpty().ifEmpty { listOfNotNull(walk?.anchor) }
                    .takeIf { it.isNotEmpty() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        DaengsFloatingButton(
            label = "← 목록",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
        )

        walk?.let {
            WalkFacts(
                walk = it,
                dogNames = dogNames(it.dogIds, pets),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 그날의 사실. **아는 것만 적는다** — 날씨를 못 받았으면 그 칸이 통째로 빠진다. */
@Composable
private fun WalkFacts(
    walk: WalkSummary,
    dogNames: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatWalkDay(walk.startedAtMillis),
                    color = TextDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                // 누구와 갔는지. **모르는 아이는 안 적는다** — 지운 강아지의 산책은
                // 이 자리가 통째로 빈다.
                if (dogNames.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dogNames.joinToString(" · "),
                        color = DaengPinkDeep,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Fact("시작", formatWalkClock(walk.startedAtMillis))
                Fact("걸은 시간", formatWalkDuration(walk.activeDurationMillis))
                Fact("거리", formatWalkDistance(walk.distanceMeters))
                walk.weather?.let { Fact("날씨", weatherLabel(it)) }
            }
            if (!walk.hasRoute) {
                Spacer(Modifier.height(10.dp))
                Text(
                    // 좌표가 한 점뿐이면 선이 안 그려진다. 지도가 비어 보이는 이유를 말해 준다.
                    "이 산책은 위치가 한 번밖에 안 잡혀서 경로가 없어요.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkFactsPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(PinkFaint), contentAlignment = Alignment.BottomCenter) {
            WalkFacts(
                walk = WalkSummary(
                    sessionId = "preview",
                    dogIds = emptyList(),
                    startedAtMillis = 1_788_324_720_000L,
                    endedAtMillis = 1_788_326_220_000L,
                    weather = null,
                    distanceMeters = 1_840.0,
                    activeDurationMillis = 1_500_000L,
                    segments = listOf(
                        listOf(
                            com.daengs.app.location.LocationSample(
                                point = GeoPoint(37.5, 127.0),
                                capturedAtMillis = 1_788_324_720_000L,
                            ),
                            com.daengs.app.location.LocationSample(
                                point = GeoPoint(37.501, 127.0),
                                capturedAtMillis = 1_788_324_820_000L,
                            ),
                        ),
                    ),
                    anchor = GeoPoint(37.5, 127.0),
                ),
                dogNames = listOf("초코"),
            )
        }
    }
}
