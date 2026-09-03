package com.daengs.app.ui.walk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daengs.app.miniroom.OutsideSnapshot
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkTrackingController

/** 앱 권한·설정·화면 이동과 산책 상태 소유자를 연결하는 진입점이다. */
@Composable
fun WalkRoute(
    onBack: () -> Unit,
    onRequestOrientation: (WalkOrientation) -> Unit,
    walkController: WalkTrackingController,
    history: WalkHistory,
    modifier: Modifier = Modifier,
    avatarBreed: DogBreed? = null,
    pets: List<Pet> = emptyList(),
    outside: OutsideSnapshot = OutsideSnapshot.DEFAULT,
    viewModel: WalkViewModel = viewModel(
        factory = WalkViewModel.factory(LocalContext.current, walkController, history),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRequested = true
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.updatePermission(
            granted = granted,
            precise = result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // 알림 권한을 거절해도 Android가 허용하는 방식으로 기록 시작은 계속한다.
        viewModel.onAction(WalkAction.StartConfirmed)
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.updatePermission(
            granted = hasLocationPermission(context),
            precise = hasPreciseLocation(context),
        )
    }

    LaunchedEffect(Unit) {
        val granted = hasLocationPermission(context)
        viewModel.activate(granted, hasPreciseLocation(context))
        if (!granted && !permissionRequested) {
            permissionRequested = true
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }
    LaunchedEffect(pets) {
        viewModel.updatePets(pets)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WalkEffect.NavigateHome -> onBack()
                WalkEffect.OpenAppSettings -> settingsLauncher.launch(appSettingsIntent(context))
                WalkEffect.RequestNotificationPermission -> {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.onAction(WalkAction.StartConfirmed)
                    }
                }
                is WalkEffect.ChangeOrientation -> onRequestOrientation(effect.orientation)
            }
        }
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::deactivate)
    }

    BackHandler { viewModel.onAction(WalkAction.Back) }

    WalkScreen(
        state = state,
        outside = outside,
        avatarBreed = avatarBreed,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun appSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

private fun hasPreciseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasLocationPermission(context: Context): Boolean =
    hasPreciseLocation(context) ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
