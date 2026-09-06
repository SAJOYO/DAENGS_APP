package com.daengs.app.ui.places.lab

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daengs.app.location.*
import com.daengs.app.place.*
import com.daengs.app.journey.*
import com.daengs.app.ui.places.*
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.flow.emptyFlow

/** 실제 개발 API + 성수 고정 중심. GPS/프로필 없이 검색 연결만 검토한다. */
class PlaceSearchLiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val baseUrl = "http://daengback.weareithero.cloud"
        val location = object : LocationSource {
            override suspend fun currentLocation() = LocationSample(GeoPoint(37.5446, 127.0559), System.currentTimeMillis())
            override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlacesViewModel(
                PlaceRepository(PlaceApi(baseUrl = { baseUrl })), HttpJourneyRepository(JourneyApi(baseUrl = { baseUrl })), location,
            ) as T
        }
        setContent {
            val vm: PlacesViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsState()
            var map by remember { mutableStateOf(false) }
            DisposableEffect(vm) { vm.activate(true); onDispose { vm.deactivate() } }
            DaengsTheme {
                Column {
                    Row {
                        Text("개발 API · 성수 고정 중심 · 프로필 없음")
                        TextButton(onClick = { map = !map }) { Text("지도") }
                    }
                    ConnectedPlaceSearchScreen(
                        state, vm::onAction, { finish() }, { vm.activate(true) }, {},
                        onCall = { phone ->
                            val safe = phone.filter { it.isDigit() || it in "+*#," }
                            if (safe.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, "tel:$safe".toUri()))
                        },
                        onOpenHandoff = { openNaverHandoff(this@PlaceSearchLiveActivity, it) }, showMap = map,
                    )
                }
            }
        }
    }
}
