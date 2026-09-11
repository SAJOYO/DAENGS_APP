package com.daengs.app.ui.game.owned

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daengs.app.pet.Pet
import com.daengs.app.territory.owned.OwnedTerritoryRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** This destination only reads ownership; no WalkViewModel, location permission or tracking service. */
@Composable
internal fun OwnedTerritoryRoute(
    repository: OwnedTerritoryRepository, ownerId: String?, pets: List<Pet>?,
    onBack: () -> Unit, onSignIn: () -> Unit,
    photoOf: (String) -> ImageBitmap? = { null },
    mapSurface: @Composable (OwnedMapPresentation, (String?) -> Unit, (com.daengs.app.map.shell.MapCameraSnapshot) -> Unit) -> Unit =
        { value, select, snapshot -> OwnedTerritoryMap(value, select, snapshot) },
) {
    var petId by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pets) {
        if (pets != null && petId != null && pets.none { it.id == petId }) petId = null
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    key(ownerId) {
        val scope = rememberCoroutineScope()
        val browser = remember(repository, petId) { ownerId?.let { OwnedTerritoryBrowser(scope, repository, it, petId) } }
        val state = key(browser) {
            browser?.state?.collectAsState()?.value ?: OwnedBrowserState(OwnedBrowserStatus.SIGN_IN)
        }
        var now by remember { mutableLongStateOf(System.nanoTime()) }
        LaunchedEffect(browser, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                browser?.refresh()
                try { awaitCancellation() } finally { browser?.stop() }
            }
        }
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { now = System.nanoTime(); delay(1_000) }
            }
        }
        OwnedTerritoryScreen(state, pets.orEmpty().takeIf { ownerId != null }.orEmpty(), petId, photoOf, now,
            onBack, onSignIn, { petId = it }, { browser?.refresh() }, { browser?.loadMore() }, mapSurface)
    }
}
