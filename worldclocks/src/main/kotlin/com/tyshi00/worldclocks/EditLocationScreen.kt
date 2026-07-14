package com.tyshi00.worldclocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditLocationState(
    val label: String = "",
    val place: String = "",
    val coordinates: String = "",
    val timeZoneId: String = "",
    val changed: Boolean = false,
)

/** Result: true if the location was renamed or deleted (caller should reload), false/null otherwise. */
class EditLocationViewModel(
    private val repo: WorldClocksRepository,
    private val locationId: Long,
) : LightViewModel<Boolean?>() {

    private val _state = MutableStateFlow(EditLocationState())
    val state: StateFlow<EditLocationState> = _state.asStateFlow()

    val hasChanged: Boolean get() = _state.value.changed

    override fun onScreenShow(screen: SimpleLightScreen<Boolean?>) {
        super.onScreenShow(screen)
        reload()
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val location = repo.getSavedLocation(locationId) ?: return@launch
            val changed = _state.value.changed
            _state.value = EditLocationState(
                label = location.label,
                place = location.fullPlaceName(),
                coordinates = "%.2f, %.2f".format(location.latitude, location.longitude),
                timeZoneId = location.timeZoneId,
                changed = changed,
            )
        }
    }

    fun rename(newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.renameLocation(locationId, trimmed)
            _state.value = _state.value.copy(label = trimmed, changed = true)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteLocation(locationId)
            withContext(Dispatchers.Main) { onDeleted() }
        }
    }
}

class EditLocationScreen(
    sealedActivity: SealedLightActivity,
    private val repo: WorldClocksRepository,
    private val locationId: Long,
) : LightScreen<Boolean?, EditLocationViewModel>(sealedActivity) {

    override val viewModelClass: Class<EditLocationViewModel>
        get() = EditLocationViewModel::class.java

    override fun createViewModel() = EditLocationViewModel(repo, locationId)

    @Composable
    override fun Content() = key(locationId) {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(viewModel.hasChanged) },
                    ),
                    center = LightTopBarCenter.Text("Edit Location"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.TRASH,
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    ConfirmActionScreen(
                                        it,
                                        message = "Remove \"${state.label}\" from your saved locations?",
                                        title = "Remove location",
                                        confirmLabel = "REMOVE",
                                    )
                                },
                                resultCallback = { confirmed ->
                                    if (confirmed == true) {
                                        viewModel.delete { goBack(true) }
                                    }
                                },
                            )
                        },
                        contentDescription = "Delete this location",
                    ),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightTextField(
                        label = "Label",
                        value = state.label,
                        placeholder = "e.g. Mom, Tokyo Office",
                        onClick = {
                            navigateTo(
                                screenFactory = { LabelLocationScreen(it, state.label) },
                                resultCallback = { newLabel -> if (newLabel != null) viewModel.rename(newLabel) },
                            )
                        },
                    )

                    Column(modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp())) {
                        LightText(text = "Location", variant = LightTextVariant.Detail)
                        LightText(
                            text = state.place,
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                        )
                    }

                    Column(modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp())) {
                        LightText(text = "Coordinates", variant = LightTextVariant.Detail)
                        LightText(
                            text = state.coordinates,
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                        )
                    }

                    Column(modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp())) {
                        LightText(text = "Time Zone", variant = LightTextVariant.Detail)
                        LightText(
                            text = state.timeZoneId,
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                        )
                    }
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}
