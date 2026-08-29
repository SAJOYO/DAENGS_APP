package com.daengs.app.ui.places

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene

/**
 * 장소 지도의 뼈대 — 네이버 지도 + 현재 위치까지만.
 *
 * 검색 패널·마커는 다음 카드다. 이 화면의 책임은 "지도가 뜨고, 내 위치가 보이고,
 * 지도를 손으로 움직이면 따라가기를 멈춘다"까지다. 검색이 들어오면 [MapScene.places]
 * 에 마커가 채워진다.
 *
 * 위치 권한은 이 화면에 들어올 때 묻는다 — 앱 시작 시점에 묻으면 지도를 안 쓰는
 * 사람에게도 팝업이 뜬다. 거절하면 위치 없이 지도만 보인다 (앱은 안 죽는다).
 */
@Composable
fun PlacesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> granted = result.values.any { it } }

    LaunchedEffect(Unit) {
        if (!granted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    var currentPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var followDevice by remember { mutableStateOf(true) }

    // 화면을 떠나면 collect 도 같이 취소된다 — 지도 화면이 떠 있는 동안만 위치를 쓴다
    // (manifest 주석과 같은 약속. 백그라운드 추적은 산책 축이고 여기 없다).
    LaunchedEffect(granted) {
        if (granted) {
            val source = FusedLocationSource(context)
            runCatching { source.currentLocation() }
                .onSuccess { currentPosition = it.point }
            source.locationUpdates().collect { sample -> currentPosition = sample.point }
        }
    }

    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize()) {
        MapHost(
            scene = MapScene(currentPosition = currentPosition),
            searchOrigin = null,
            followDevice = followDevice,
            onCameraIdle = { /* 검색 카드에서 "이 지역 검색"의 재료가 된다 */ },
            onCameraGesture = { followDevice = false },
            onSelectPlace = { /* 마커는 다음 카드 */ },
            modifier = Modifier.fillMaxSize(),
        )
        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
        ) { Text("← 홈") }
        if (granted) {
            FilledTonalButton(
                onClick = { followDevice = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) { Text("내 위치") }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
