package com.daengs.app.miniroom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.time.LocalTime

/**
 * 지금 창밖에 보여줄 것. **실제 시각과 날씨를 따른다.**
 *
 * 흐름은 셋이다.
 *
 *   1. 위치 권한을 묻는다 (coarse). 거부하면 2로 안 간다
 *   2. 마지막으로 알려진 위치를 읽는다 — 새로 측정하지 않는다
 *   3. 그 좌표로 [OutsideApi] 를 한 번 부른다
 *
 * **어디서 막히든 창밖이 비면 안 된다.** 권한 거부 · 위치 없음 · 비행기 모드 ·
 * 응답 실패 전부 [fallback] 으로 떨어진다. 장식이라 조용히 물러나는 게 맞다 —
 * 창문 하나 때문에 오류 문구를 띄우지 않는다.
 *
 * **위치를 새로 측정하지 않는 이유**: 창밖 그림은 시·군 단위면 충분한데 측위를
 * 걸면 배터리와 시간을 쓴다. 마지막 위치가 없으면 그냥 폴백이다.
 */
@Composable
fun rememberOutsideView(): State<OutsideView> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(fallback()) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 권한을 막 받았다. 아래 LaunchedEffect 가 다시 돌도록 표시만 바꾼다.
            asked = false
        }
    }

    LaunchedEffect(asked) {
        if (asked) return@LaunchedEffect
        asked = true
        if (!hasLocationPermission(context)) {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return@LaunchedEffect
        }
        val where = lastKnownLocation(context) ?: return@LaunchedEffect
        OutsideApi.fetch(where.latitude, where.longitude)?.let { state.value = it }
    }

    return state
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 마지막으로 알려진 위치. 공급자를 훑어 **가장 최근 것**을 고른다.
 *
 * 하나만 물으면(예: `NETWORK_PROVIDER`) 그 공급자가 꺼져 있을 때 빈손이 된다.
 */
private fun lastKnownLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return runCatching {
        lm.getProviders(true)
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }.getOrNull()
}

/**
 * 날씨를 못 읽었을 때.
 *
 * 낮·밤만 기기 시각으로 어림잡고 날씨는 맑음이다. **어림값인 것을 알고 쓴다** —
 * 6시~18시로 자르면 계절에 따라 한두 시간씩 틀리지만, 폴백은 "그럴듯하면 된다".
 * 제대로 된 낮·밤은 [OutsideApi] 가 주는 `is_day` 다.
 */
private fun fallback(): OutsideView {
    val hour = LocalTime.now().hour
    val time = if (hour in 6..17) OutsideTime.DAY else OutsideTime.NIGHT
    return OutsideView.of(time, OutsideWeather.CLEAR)
}
