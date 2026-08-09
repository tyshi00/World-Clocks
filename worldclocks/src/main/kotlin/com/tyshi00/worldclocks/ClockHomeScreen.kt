package com.tyshi00.worldclocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

data class HomeState(
    val timeText: String = "--:--",
    val dateText: String = "",
    val weekdayText: String = "",
    val gmtOffsetText: String = "",
    val homeLayout: HomeLayout = HomeLayout.CLASSIC,
    val savedRows: List<SavedLocationRow> = emptyList(),
)

class ClockHomeViewModel(private val repo: WorldClocksRepository) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var tickJob: Job? = null

    // Only reloaded from the database on screen show (and whenever a child
    // screen pops back to Home, which re-triggers show) - not on every tick,
    // since the tick just needs to reformat already-known locations' times,
    // not re-query which locations exist every second.
    private var savedLocations: List<SavedLocation> = emptyList()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.getInvertColors()) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
            FormatPreferencesHolder.timeFormat = repo.getTimeFormat()
            FormatPreferencesHolder.dateFormat = repo.getDateFormat()
            FormatPreferencesHolder.homeLayout = repo.getHomeLayout()
            savedLocations = repo.getSavedLocations()
        }
        startTicking()
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        tickJob?.cancel()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                refresh()
                delay(1_000L)
            }
        }
    }

    private fun refresh() {
        // Home always reflects the phone's own configured time zone - no
        // location permission needed, and it stays correct automatically if
        // the person travels and their device's zone updates.
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val rows = savedLocations.map { location ->
            val locationNow = ZonedDateTime.now(location.zoneId)
            SavedLocationRow(
                id = location.id,
                label = location.label,
                place = location.fullPlaceName(),
                timeText = ClockFormatting.time(locationNow, FormatPreferencesHolder.timeFormat),
                gmtText = ClockFormatting.gmtOffsetLabel(locationNow),
            )
        }
        _state.value = HomeState(
            timeText = ClockFormatting.time(now, FormatPreferencesHolder.timeFormat),
            dateText = ClockFormatting.date(now, FormatPreferencesHolder.dateFormat),
            weekdayText = ClockFormatting.weekdayLabel(now),
            gmtOffsetText = ClockFormatting.gmtOffsetLabel(now),
            homeLayout = FormatPreferencesHolder.homeLayout,
            savedRows = rows,
        )
    }

    /** Split view rows are tappable shortcuts into Compare, pre-selecting that location. */
    fun selectLocationForCompare(locationId: Long, onReady: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setCompareTargetId(locationId)
            withContext(Dispatchers.Main) { onReady() }
        }
    }
}

@InitialScreen
class ClockHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ClockHomeViewModel>(sealedActivity) {

    private val repo = WorldClocksRepository.getInstance {
        lightContext.buildDatabase(WorldClocksDatabase::class.java, "world_clocks.db")
    }

    override val viewModelClass: Class<ClockHomeViewModel>
        get() = ClockHomeViewModel::class.java

    override fun createViewModel() = ClockHomeViewModel(repo)

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
                    center = LightTopBarCenter.Text("World Clocks"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = { navigateTo(screenFactory = { AppSettingsScreen(it, repo) }) },
                    ),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                if (state.homeLayout == HomeLayout.SPLIT) {
                    SplitHomeContent(
                        state = state,
                        onLocationTap = { locationId ->
                            viewModel.selectLocationForCompare(locationId) {
                                navigateTo(screenFactory = { ComparisonScreen(it, repo) })
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    ClassicHomeContent(
                        state = state,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.ADD,
                            onClick = {
                                navigateTo(
                                    screenFactory = { LocationMapScreen(it, repo) },
                                )
                            },
                            contentDescription = "Add a location",
                        ),
                        LightBarButton.Text(
                            text = "COMPARE",
                            onClick = {
                                navigateTo(screenFactory = { ComparisonScreen(it, repo) })
                            },
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.LIST,
                            onClick = {
                                navigateTo(screenFactory = { SavedLocationsScreen(it, repo) })
                            },
                            contentDescription = "Saved locations",
                        ),
                    ),
                )
            }
        }
    }
}

/** Today's layout: one big centered clock, filling the whole available area. */
@Composable
private fun ClassicHomeContent(state: HomeState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 1f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText(
            text = "Local Time",
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
        )
        LightText(
            text = state.timeText,
            variant = LightTextVariant.Title,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
        )
        LightText(
            text = "${state.weekdayText}, ${state.dateText}",
            variant = LightTextVariant.Copy,
            align = TextAlign.Center,
        )
        LightText(
            text = state.gmtOffsetText,
            variant = LightTextVariant.Fine,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
        )
    }
}

/**
 * 30/70 split: local time (smaller, left) next to saved locations (label +
 * time only, right - full place name/GMT/edit/delete/sort all stay on the
 * dedicated Saved Locations screen, reached the same way as in Classic).
 * Text is left to wrap naturally in each column rather than shrinking font
 * size to force a fit.
 */
@Composable
private fun SplitHomeContent(
    state: HomeState,
    onLocationTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .padding(horizontal = 0.6f.gridUnitsAsDp()),
            verticalArrangement = Arrangement.Center,
        ) {
            LightText(text = "Local Time", variant = LightTextVariant.Detail, lighten = true)
            LightText(
                text = state.timeText,
                variant = LightTextVariant.Subtitle,
                modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
            )
            LightText(text = "${state.weekdayText}, ${state.dateText}", variant = LightTextVariant.Fine)
            LightText(text = state.gmtOffsetText, variant = LightTextVariant.Fine, lighten = true)
        }

        LightScrollView(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
                .padding(horizontal = 0.75f.gridUnitsAsDp()),
        ) {
            if (state.savedRows.isEmpty()) {
                LightText(
                    text = "No saved locations yet. Use the + button below to add one.",
                    variant = LightTextVariant.Fine,
                    lighten = true,
                )
            } else {
                state.savedRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { onLocationTap(row.id) }
                            .padding(vertical = 0.5f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(
                            text = row.label,
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.weight(1f).padding(end = 0.5f.gridUnitsAsDp()),
                        )
                        LightText(text = row.timeText, variant = LightTextVariant.Fine)
                    }
                }
            }
        }
    }
}
