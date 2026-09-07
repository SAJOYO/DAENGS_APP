package com.daengs.app.map.style

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class WalkStyleSelection(val policy: WalkStylePolicy, val themeId: String)

/** Device display preference, shared by active and completed maps; no location is saved here. */
class WalkStyleStore(context: Context) {
    val preferences: SharedPreferences = context.applicationContext.getSharedPreferences("walk-map-style", Context.MODE_PRIVATE)
    private val bundled = context.assets.open("walk-style-v1.json").bufferedReader().use { it.readText() }

    fun read(): WalkStyleSelection {
        val policy = runCatching { WalkStylePolicy.parse(preferences.getString("policy", bundled) ?: bundled) }
            .getOrElse { WalkStylePolicy.parse(bundled) }
        val requested = runCatching { preferences.getString("theme", policy.defaultTheme) }.getOrNull()
        return WalkStyleSelection(policy, policy.theme(requested.orEmpty()).id)
    }
    fun select(id: String) {
        preferences.edit().putString("theme", read().policy.theme(id).id).apply()
    }
    fun cachePolicy(text: String) {
        WalkStylePolicy.parse(text) // Validate before replacing the last usable policy.
        preferences.edit().putString("policy", text).apply()
    }
}

@Composable
fun rememberWalkStyle(): State<WalkStyleSelection> {
    val context = LocalContext.current
    val store = remember(context) { WalkStyleStore(context) }
    val state = remember(store) { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = store.read() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
