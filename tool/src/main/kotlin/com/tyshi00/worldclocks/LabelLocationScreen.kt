package com.tyshi00.worldclocks

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController

/**
 * Used both when saving a brand-new location (pre-filled with the city name)
 * and when renaming an existing one (pre-filled with its current label).
 * Result is the trimmed label the person entered, or null if they backed out.
 */
class LabelLocationScreen(
    sealedActivity: SealedLightActivity,
    private val suggestedLabel: String,
) : SimpleLightScreen<String>(sealedActivity) {

    // LightTextInputEditor's editorKey defaults to its title, which is a
    // fixed constant string below — every visit to this screen would
    // otherwise share that same key and reuse stale editor/keyboard state
    // from a previous visit (e.g. backspace not affecting the currently
    // displayed text). A key unique to this specific screen instance
    // forces a genuinely fresh editor session each time.
    private val editorKey = System.nanoTime()

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state = rememberTextFieldState(suggestedLabel)
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Label this location",
                editorKey = editorKey,
                state = state,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { text ->
                    val trimmed = text.toString().trim()
                    if (trimmed.isNotEmpty()) goBack(trimmed)
                },
                onBack = { goBack(null) },
                submitLabel = "CONFIRM",
                singleLine = true,
            )
        }
    }
}
