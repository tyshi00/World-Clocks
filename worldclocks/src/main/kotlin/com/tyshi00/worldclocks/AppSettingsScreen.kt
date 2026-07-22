package com.tyshi00.worldclocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSettingsState(
    val timeFormat: TimeFormat = TimeFormat.AM_PM,
    val dateFormat: DateFormat = DateFormat.MDY,
    val homeLayout: HomeLayout = HomeLayout.CLASSIC,
    val invertColors: Boolean = false,
)

class AppSettingsViewModel(private val repo: WorldClocksRepository) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(AppSettingsState())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        reload()
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = AppSettingsState(
                timeFormat = repo.getTimeFormat(),
                dateFormat = repo.getDateFormat(),
                homeLayout = repo.getHomeLayout(),
                invertColors = repo.getInvertColors(),
            )
        }
    }

    fun setTimeFormat(format: TimeFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setTimeFormat(format)
            FormatPreferencesHolder.timeFormat = format
            _state.value = _state.value.copy(timeFormat = format)
        }
    }

    fun setDateFormat(format: DateFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setDateFormat(format)
            FormatPreferencesHolder.dateFormat = format
            _state.value = _state.value.copy(dateFormat = format)
        }
    }

    fun setHomeLayout(layout: HomeLayout) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setHomeLayout(layout)
            FormatPreferencesHolder.homeLayout = layout
            _state.value = _state.value.copy(homeLayout = layout)
        }
    }

    fun toggleInvertColors() {
        viewModelScope.launch(Dispatchers.IO) {
            val newValue = !_state.value.invertColors
            repo.setInvertColors(newValue)
            _state.value = _state.value.copy(invertColors = newValue)
            if (newValue) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
        }
    }

    fun deleteAllSavedData() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteAllSavedData()
        }
    }
}

class AppSettingsScreen(
    sealedActivity: SealedLightActivity,
    private val repo: WorldClocksRepository,
) : LightScreen<Unit, AppSettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<AppSettingsViewModel>
        get() = AppSettingsViewModel::class.java

    override fun createViewModel() = AppSettingsViewModel(repo)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Settings"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable {
                                navigateTo(
                                    screenFactory = { TimeFormatScreen(it, state.timeFormat) },
                                    resultCallback = { result -> if (result != null) viewModel.setTimeFormat(result) },
                                )
                            }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LightText(text = "Time format", variant = LightTextVariant.Copy)
                            LightText(text = state.timeFormat.label, variant = LightTextVariant.Fine, lighten = true)
                        }
                        LightIcon(icon = LightIcons.ARROW_RIGHT)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable {
                                navigateTo(
                                    screenFactory = { DateFormatScreen(it, state.dateFormat) },
                                    resultCallback = { result -> if (result != null) viewModel.setDateFormat(result) },
                                )
                            }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LightText(text = "Date format", variant = LightTextVariant.Copy)
                            LightText(text = state.dateFormat.label, variant = LightTextVariant.Fine, lighten = true)
                        }
                        LightIcon(icon = LightIcons.ARROW_RIGHT)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable {
                                navigateTo(
                                    screenFactory = { HomeLayoutScreen(it, state.homeLayout) },
                                    resultCallback = { result -> if (result != null) viewModel.setHomeLayout(result) },
                                )
                            }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LightText(text = "Home screen", variant = LightTextVariant.Copy)
                            LightText(text = state.homeLayout.label, variant = LightTextVariant.Fine, lighten = true)
                        }
                        LightIcon(icon = LightIcons.ARROW_RIGHT)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { viewModel.toggleInvertColors() }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(
                            text = "Invert colors",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.weight(1f),
                        )
                        // Screen is black (dark theme) when invertColors is false,
                        // so show TOGGLE_ON (knob on the left) in that state.
                        LightIcon(icon = if (state.invertColors) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON)
                    }

                    LightText(
                        text = "Delete all saved data",
                        variant = LightTextVariant.Copy,
                        lighten = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable {
                                navigateTo(
                                    screenFactory = {
                                        ConfirmActionScreen(
                                            it,
                                            message = "Delete all saved locations? This can't be undone.",
                                            title = "Delete all data",
                                            confirmLabel = "DELETE",
                                        )
                                    },
                                    resultCallback = { confirmed -> if (confirmed == true) viewModel.deleteAllSavedData() },
                                )
                            }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                    )
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}
