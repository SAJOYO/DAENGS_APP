package com.daengs.app.ui.places.lab

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daengs.app.place.toPlaceSearchResponse
import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.map.shell.MapScene
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Debug manifest 전용 진입점. 로그인·프로필·검색 서버·AI 호출 없이 기록된 응답 사용. */
class PlaceSearchLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = PlaceLabSource { criteria ->
            withContext(Dispatchers.IO) {
                val root = assets.open("place_search_lab.json").bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
                val key = "${criteria.region}-large${if (criteria.parkingFirst) "_parking" else ""}"
                val response = root.getValue("cases").jsonObject.getValue(key).jsonObject.toPlaceSearchResponse()
                val matching = response.groups.filter { criteria.kind == null || it.kind == criteria.kind }
                    .flatMap { it.results }.distinctBy { it.place.key }.filter {
                        criteria.query.isBlank() || "${it.place.name} ${it.place.facts.address.orEmpty()}".contains(criteria.query, ignoreCase = true)
                    }
                if (criteria.kind != null) matching else matching.sortedWith(
                    compareBy<com.daengs.app.place.PlaceSearchHit> { if (criteria.parkingFirst) it.place.distanceMeters / 500 else 0 }
                        .thenBy { if (criteria.parkingFirst && it.place.facts.parking == true) 0 else 1 }
                        .thenBy { it.place.distanceMeters }
                )
            }
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaceSearchLabViewModel(source) as T
        }
        setContent {
            val vm: PlaceSearchLabViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsState()
            var nativeMap by remember { mutableStateOf(false) }
            DaengsTheme {
                Column {
                    Row {
                        TextButton(onClick = { vm.region(if (state.applied.region == "seongsu") "gangnam" else "seongsu") }) { Text("개발용 · ${state.applied.region}") }
                        TextButton(onClick = { nativeMap = !nativeMap }) { Text(if (nativeMap) "지도 끄기" else "지도 켜기") }
                        TextButton(onClick = { vm.simulate(LabPhase.ERROR) }) { Text("오류") }
                    }
                    Box(Modifier.weight(1f)) {
                        PlaceSearchLabScreen(state, vm::edit, vm::toggleAi, vm::submit, vm::category, vm::parking, vm::dog, vm::toggle, vm::retry,
                            onAction = { Toast.makeText(this@PlaceSearchLabActivity, it, Toast.LENGTH_SHORT).show() },
                            map = {
                                val visible = state.hits.takeIf { state.phase == LabPhase.RESULTS }.orEmpty()
                                if (nativeMap) {
                                    MapHost(
                                        scene = MapScene(places = visible.map { hit -> PlaceMarkerState(
                                            id = placeMarkerId(hit.place.key), point = hit.place.point, label = hit.place.name,
                                            selected = hit.place.key == state.selected, iconGroup = hit.place.iconGroup,
                                        ) }), searchOrigin = visible.firstOrNull()?.place?.point, followDevice = false,
                                        fitBounds = visible.map { it.place.point }, onCameraIdle = {}, onCameraGesture = {},
                                        onSelectPlace = { id -> visible.find { placeMarkerId(it.place.key) == id }?.let { vm.select(it.place.key) } },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("시설 선택 검토 · 지도는 명시적으로 켜야 로드됩니다.")
                                        androidx.compose.foundation.lazy.LazyRow {
                                            items(visible.size) { index -> TextButton(onClick = { vm.select(visible[index].place.key) }) { Text("${index + 1}") } }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
