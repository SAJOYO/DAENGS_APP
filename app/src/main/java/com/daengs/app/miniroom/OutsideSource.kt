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
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 지금 창밖에 보여줄 것. **실제 시각과 날씨를 따른다.**
 *
 * 흐름은 셋이다.
 *
 *   1. 위치 권한을 묻는다. 거부하면 2로 안 간다
 *   2. 마지막으로 알려진 위치를 읽는다 — 새로 측정하지 않는다
 *   3. 그 좌표로 [OutsideApi] 를 한 번 부른다
 *
 * **어디서 막히든 창밖이 비면 안 된다.** 권한 거부 · 위치 없음 · 비행기 모드 ·
 * 응답 실패 전부 [fallback] 으로 떨어진다. 장식이라 조용히 물러나는 게 맞다 —
 * 창문 하나 때문에 오류 문구를 띄우지 않는다.
 *
 * **위치를 새로 측정하지 않는 이유**: 창밖 그림은 시·군 단위면 충분한데 측위를
 * 걸면 배터리와 시간을 쓴다. 마지막 위치가 없으면 그냥 폴백이다.
 *
 * ⚠️ **화면이 아니라 [MainActivity] 에서 부른다.**
 *
 * 상태를 `remember` 로 들고 있어서, 이걸 부르는 컴포저블이 컴포지션에서 빠지면
 * 받아 둔 날씨가 같이 사라진다. 예전에는 `HomeScreen` 안에서 불렀는데, 홈은
 * `when (screen)` 이 갈아끼우는 자리라 도감·산책·챗봇을 갔다 오면 **매번 폴백(맑은
 * 낮, 기온 없음)부터 다시 시작했다.** 사용자 눈에는 날씨 카드와 오늘의 한 마디가
 * 눈앞에서 한 번 바뀌는 것으로 보였고, 느린 망에서는 그 상태가 6초까지 갔다.
 *
 * 15분 갱신도 그때는 죽은 설계였다 — 홈에 머무는 동안만 살아 있고, 홈에 올 때마다
 * 새로 받았다. 뿌리에서 부르면 앱이 사는 동안 한 벌만 돌고, 위치 권한도 한 번만 묻는다.
 */
@Composable
fun rememberOutsideView(): State<OutsideSnapshot> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(fallback()) }

    // **권한 상태가 열쇠다.** 없으면 물어보고, 받으면 그때 부른다.
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }

    // 권한 창은 한 번만 띄운다. **이 값은 아래 효과의 열쇠가 아니다** — 열쇠로 쓰면
    // 효과 안에서 바꾸는 순간 자기 자신이 취소된다 (아래 주석).
    val requested = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.any { it }
    }

    // ⚠️ **효과 안에서 열쇠를 바꾸면 안 된다.**
    //
    // 예전에는 `asked` 하나를 열쇠로 쓰면서 효과 안에서 `asked = true` 로 바꿨다.
    // 그러면 다음 조합에서 LaunchedEffect 가 새 열쇠로 다시 시작하면서 **돌고 있던
    // 코루틴을 취소한다.** 날씨 요청은 네트워크라 그 전에 못 끝나서, 창밖은 언제나
    // 폴백(맑은 낮)이었다 — 비가 와도 해가 떠 있었다.
    //
    // 열쇠는 권한 상태뿐이다. 효과는 이 값을 **읽기만** 한다.
    LaunchedEffect(granted) {
        if (!granted) {
            if (requested.value) return@LaunchedEffect
            requested.value = true
            // **정확한 위치까지 같이 묻는다.**
            //
            // 창밖 그림만 보면 대략적인 위치로 충분하다. 그런데 여기가 앱을 켜고
            // **처음이자 유일하게** 위치를 묻는 자리라, 여기서 coarse 만 물으면
            // 사용자는 "대략적인 위치" 를 고른 채로 굳는다 — 그 뒤 산책 화면에서 정밀
            // 위치가 필요해도 **권한 창이 다시 안 뜬다**(안드로이드가 한 번만 보여 준다).
            // 실기기에서 실제로 그렇게 굳어서 산책 지도가 엉뚱한 자리를 가리켰다.
            //
            // 둘을 같이 요청하면 시스템 창에 "정확한 위치 / 대략적인 위치" 선택이
            // 같이 뜬다. 고르는 것은 사용자 몫이고, 대략적인 위치를 골라도 창밖은 돈다.
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return@LaunchedEffect
        }
        // **한 번 받고 끝내지 않는다.** 방을 열어 둔 채로 비가 그치기도 하고, 켤 때
        // 마지막 위치가 없어 빈손이었다가 나중에 생기기도 한다.
        while (true) {
            lastKnownLocation(context)?.let { where ->
                OutsideApi.fetchNow(where.latitude, where.longitude)?.let {
                    state.value = OutsideSnapshot.of(it)
                }
            }
            delay(REFRESH_MS)
        }
    }

    return state
}

/**
 * 창밖을 다시 보는 간격.
 *
 * 날씨는 분 단위로 안 바뀌고, 이건 장식이라 자주 부를 이유가 없다. Open-Meteo 도
 * 15분마다 갱신한다.
 */
private const val REFRESH_MS = 15 * 60 * 1000L

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
private fun fallback(): OutsideSnapshot {
    val hour = LocalTime.now().hour
    val time = if (hour in 6..17) OutsideTime.DAY else OutsideTime.NIGHT
    // **기온은 안 지어낸다.** 모르는 값을 넣으면 "많이 추워요" 가 한여름에 뜬다.
    return OutsideSnapshot(OutsideView.of(time, OutsideWeather.CLEAR), temperatureC = null)
}
