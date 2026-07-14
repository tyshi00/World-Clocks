package com.tyshi00.worldclocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

data class ComparisonState(
    val hasSavedLocations: Boolean = false,
    val targetId: Long? = null,
    val targetLabel: String = "",
    val localTime: String = "--:--",
    val localDate: String = "",
    val targetTime: String = "--:--",
    val targetDate: String = "",
    val differenceText: String = "",
    val dayNote: String? = null,
)

class ComparisonViewModel(private val repo: WorldClocksRepository) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(ComparisonState())
    val state: StateFlow<ComparisonState> = _state.asStateFlow()

    private var tickJob: Job? = null

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        startTicking()
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        tickJob?.cancel()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refresh()
                delay(30_000L)
            }
        }
    }

    private suspend fun refresh() {
        val saved = repo.getSavedLocations()
        if (saved.isEmpty()) {
            _state.value = ComparisonState(hasSavedLocations = false)
            return
        }

        var targetId = repo.getCompareTargetId()
        var target = saved.firstOrNull { it.id == targetId }
        if (target == null) {
            // No target chosen yet (or the previous one was deleted) — default
            // to the first saved location and remember that choice.
            target = saved.first()
            targetId = target.id
            repo.setCompareTargetId(targetId)
        }

        val localZone = ZoneId.systemDefault()
        val targetZone = target.zoneId
        val localNow = ZonedDateTime.now(localZone)
        val targetNow = ZonedDateTime.now(targetZone)

        _state.value = ComparisonState(
            hasSavedLocations = true,
            targetId = targetId,
            targetLabel = target.label,
            localTime = ClockFormatting.time(localNow, FormatPreferencesHolder.timeFormat),
            localDate = ClockFormatting.date(localNow, FormatPreferencesHolder.dateFormat),
            targetTime = ClockFormatting.time(targetNow, FormatPreferencesHolder.timeFormat),
            targetDate = ClockFormatting.date(targetNow, FormatPreferencesHolder.dateFormat),
            differenceText = ClockFormatting.differenceSummary(localZone, targetZone),
            dayNote = ClockFormatting.dayOffsetNote(localNow, targetNow),
        )
    }

    fun setTarget(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setCompareTargetId(id)
            refresh()
        }
    }
}

class ComparisonScreen(
    sealedActivity: SealedLightActivity,
    private val repo: WorldClocksRepository,
) : LightScreen<Unit, ComparisonViewModel>(sealedActivity) {

    override val viewModelClass: Class<ComparisonViewModel>
        get() = ComparisonViewModel::class.java

    override fun createViewModel() = ComparisonViewModel(repo)

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
                    center = LightTopBarCenter.Text("Compare"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                if (!state.hasSavedLocations) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LightText(
                            text = "Save a location first to compare time zones.",
                            variant = LightTextVariant.Copy,
                            align = TextAlign.Center,
                            lighten = true,
                        )
                    }
                    LightBottomBar(
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.ADD,
                                onClick = { navigateTo(screenFactory = { LocationMapScreen(it, repo) }) },
                                contentDescription = "Add a location",
                            ),
                        ),
                    )
                } else {
                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        LightTextField(
                            label = "Comparing to",
                            value = state.targetLabel,
                            placeholder = "Choose a saved location",
                            onClick = {
                                navigateTo(
                                    screenFactory = { ComparisonPickerScreen(it, repo, state.targetId) },
                                    resultCallback = { id -> if (id != null) viewModel.setTarget(id) },
                                )
                            },
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2f.gridUnitsAsDp()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LightText(text = "You", variant = LightTextVariant.Detail, lighten = true)
                            LightText(text = state.localTime, variant = LightTextVariant.Subtitle)
                            LightText(text = state.localDate, variant = LightTextVariant.Fine, lighten = true)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2f.gridUnitsAsDp()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LightText(text = state.targetLabel, variant = LightTextVariant.Detail, lighten = true)
                            LightText(text = state.targetTime, variant = LightTextVariant.Subtitle)
                            LightText(text = state.targetDate, variant = LightTextVariant.Fine, lighten = true)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2f.gridUnitsAsDp()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LightText(
                                text = state.differenceText,
                                variant = LightTextVariant.Copy,
                                align = TextAlign.Center,
                            )
                            state.dayNote?.let {
                                LightText(
                                    text = it,
                                    variant = LightTextVariant.Fine,
                                    lighten = true,
                                    align = TextAlign.Center,
                                )
                            }
                        }
                    }

                    LightBottomBar(items = listOf())
                }
            }
        }
    }
}
