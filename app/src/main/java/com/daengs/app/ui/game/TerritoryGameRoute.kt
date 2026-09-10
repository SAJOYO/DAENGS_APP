package com.daengs.app.ui.game

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daengs.app.activity.ActivityRepository
import com.daengs.app.pet.Pet
import kotlinx.coroutines.delay

/** Selection is for reading scores only; it never writes a primary pet or walk participants. */
@Composable
fun TerritoryGameRoute(
    repository: ActivityRepository, ownerId: String?, pets: List<Pet>?,
    onBack: () -> Unit, onOpenMap: () -> Unit,
    photoOf: (String) -> ImageBitmap? = { null },
    petsError: Boolean = false, onRefreshPets: () -> Unit = {},
    onAddPet: () -> Unit = onBack, onSignIn: () -> Unit = onBack,
    onOpenBookmarks: () -> Unit = onBack,
) {
    var selectedId by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    val pet = pets?.firstOrNull { it.id == selectedId } ?: pets?.firstOrNull { it.isPrimary } ?: pets?.firstOrNull()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    key(ownerId, pet?.id) {
        var overview by remember { mutableStateOf(TerritoryGameOverview()) }
        var retry by remember { mutableIntStateOf(0) }
        var now by remember { mutableLongStateOf(System.nanoTime()) }
        LaunchedEffect(repository, ownerId, pet?.id, retry, lifecycle) {
            if (ownerId != null && pet != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    overview = loadGameOverview(repository, pet.id)
                    delay(30_000)
                }
            }
        }
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { now = System.nanoTime(); delay(1_000) }
            }
        }
        val shown = when {
            ownerId == null -> TerritoryGameOverview(GameOverviewStatus.SIGN_IN)
            pets == null -> TerritoryGameOverview(if (petsError) GameOverviewStatus.PETS_ERROR else GameOverviewStatus.PETS_LOADING)
            pet == null -> TerritoryGameOverview(GameOverviewStatus.NO_PET)
            else -> overview
        }
        TerritoryGameScreen(shown, pet.takeIf { ownerId != null }, pets.orEmpty().takeIf { ownerId != null }.orEmpty(), photoOf, now,
            onBack = onBack, onOpenMap = onOpenMap, onSelectPet = { selectedId = it },
            onRetry = { if (pets == null) onRefreshPets() else { overview = TerritoryGameOverview(); retry++ } },
            onAddPet = onAddPet, onSignIn = onSignIn, onOpenBookmarks = onOpenBookmarks)
    }
}
