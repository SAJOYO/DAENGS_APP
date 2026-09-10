package com.daengs.app.ui.walk.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.daengs.app.auth.AccountScope
import java.util.UUID

// AccountScope.generation starts again at zero in a new process. Its old UI snapshot must not
// accidentally match a new login then. Configuration recreation keeps this process epoch.
private val WalkRecordsProcessEpoch = UUID.randomUUID().toString()

/** Retains UI values for one login, while the records screen and its native map are unmounted. */
@Stable
internal class WalkRecordsRouteState private constructor(
    internal val accountScope: AccountScope,
    private var savedValues: Map<String, List<Any?>> = emptyMap(),
    openedSessionId: String? = null,
) {
    var openedSessionId by mutableStateOf(openedSessionId)
        private set

    private var activeRegistry: SaveableStateRegistry? = null

    fun open(sessionId: String) {
        require(sessionId.isNotBlank())
        captureRecords()
        openedSessionId = sessionId
    }

    fun closeDetail() { openedSessionId = null }

    /** Call before leaving records; disposal happens after child providers may be unregistered. */
    fun captureRecords() {
        activeRegistry?.let { savedValues = it.performSave() }
    }

    internal fun registry(canBeSaved: (Any) -> Boolean): SaveableStateRegistry =
        SaveableStateRegistry(savedValues, canBeSaved)

    internal fun attach(registry: SaveableStateRegistry) {
        check(activeRegistry == null || activeRegistry === registry) { "Records may only be mounted once." }
        activeRegistry = registry
    }

    internal fun detach(registry: SaveableStateRegistry) {
        // Never replace the explicit navigation snapshot with a partially disposed registry.
        if (activeRegistry === registry) activeRegistry = null
    }

    internal companion object {
        fun initial(scope: AccountScope) = WalkRecordsRouteState(scope)

        fun saver(scope: AccountScope, processEpoch: String = WalkRecordsProcessEpoch):
            Saver<WalkRecordsRouteState, List<Any?>> = Saver(
            save = { state ->
                // Activity saving while records are mounted must read the latest UI, even when
                // neither open() nor captureRecords() has been called since the last edit.
                state.captureRecords()
                listOf(state.accountScope.ownerId, state.accountScope.generation, processEpoch,
                    state.savedValues, state.openedSessionId)
            },
            restore = { value ->
                // rememberSaveable(inputs) does not validate the inputs of restored state.
                if (value.size != 5 || value[0] != scope.ownerId || value[1] != scope.generation ||
                    value[2] != processEpoch) null
                else {
                    @Suppress("UNCHECKED_CAST")
                    val saved = value[3] as? Map<String, List<Any?>>
                    saved?.let { WalkRecordsRouteState(scope, it, value[4] as? String) }
                }
            },
        )
    }
}

@Composable
internal fun rememberWalkRecordsRouteState(accountScope: AccountScope): WalkRecordsRouteState {
    val saver = remember(accountScope) { WalkRecordsRouteState.saver(accountScope) }
    return rememberSaveable(accountScope, saver = saver) { WalkRecordsRouteState.initial(accountScope) }
}

/** A registry boundary only: native maps must not enter SaveableStateHolder's ReusableContent. */
@Composable
internal fun RetainedWalkRecords(state: WalkRecordsRouteState, content: @Composable () -> Unit) {
    val parent = LocalSaveableStateRegistry.current
    key(state.accountScope.ownerId, state.accountScope.generation) {
        val registry = remember(state, parent) { state.registry { parent?.canBeSaved(it) ?: true } }
        DisposableEffect(state, registry) {
            state.attach(registry)
            onDispose { state.detach(registry) }
        }
        // Child positional keys stay private; only the holder's saver is registered in the parent.
        // The lifecycle and SavedStateRegistryOwner inherited by AndroidView are unchanged.
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
    }
}
