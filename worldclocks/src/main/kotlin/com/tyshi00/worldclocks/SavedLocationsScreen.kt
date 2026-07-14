package com.tyshi00.worldclocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

data class SavedLocationRow(
    val id: Long,
    val label: String,
    val place: String,
    val timeText: String,
    val gmtText: String,
)

data class SavedLocationsState(
    val rows: List<SavedLocationRow> = emptyList(),
    val reorderMode: Boolean = false,
)

class SavedLocationsViewModel(private val repo: WorldClocksRepository) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(SavedLocationsState())
    val state: StateFlow<SavedLocationsState> = _state.asStateFlow()

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
                reload()
                delay(30_000L)
            }
        }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = repo.getSavedLocations().map { it.toRow() }
            _state.value = _state.value.copy(rows = rows)
        }
    }

    private fun SavedLocation.toRow(): SavedLocationRow {
        val now = ZonedDateTime.now(zoneId)
        return SavedLocationRow(
            id = id,
            label = label,
            place = fullPlaceName(),
            timeText = ClockFormatting.time(now, FormatPreferencesHolder.timeFormat),
            gmtText = ClockFormatting.gmtOffsetLabel(now),
        )
    }

    fun toggleReorderMode() {
        _state.value = _state.value.copy(reorderMode = !_state.value.reorderMode)
    }

    fun moveUp(id: Long) = move(id, -1)
    fun moveDown(id: Long) = move(id, 1)

    private fun move(id: Long, direction: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.moveLocation(id, direction)
            reload()
        }
    }
}

class SavedLocationsScreen(
    sealedActivity: SealedLightActivity,
    private val repo: WorldClocksRepository,
) : LightScreen<Unit, SavedLocationsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SavedLocationsViewModel>
        get() = SavedLocationsViewModel::class.java

    override fun createViewModel() = SavedLocationsViewModel(repo)

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
                    center = LightTopBarCenter.Text("Saved Locations"),
                    rightButton = LightBarButton.Text(
                        text = if (state.reorderMode) "DONE" else "SORT",
                        onClick = { viewModel.toggleReorderMode() },
                    ),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                if (state.rows.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = "No saved locations yet. Use the + button on the home screen to add one.",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                    }
                } else {
                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        state.rows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { rowModifier ->
                                        if (!state.reorderMode) {
                                            rowModifier.lightClickable {
                                                navigateTo(
                                                    screenFactory = { EditLocationScreen(it, repo, row.id) },
                                                    resultCallback = { changed ->
                                                        if (changed == true) viewModel.reload()
                                                    },
                                                )
                                            }
                                        } else {
                                            rowModifier
                                        }
                                    }
                                    .padding(vertical = 0.75f.gridUnitsAsDp()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    LightText(text = row.label, variant = LightTextVariant.Copy)
                                    LightText(
                                        text = "${row.place} · ${row.gmtText}",
                                        variant = LightTextVariant.Fine,
                                        lighten = true,
                                    )
                                }
                                if (state.reorderMode) {
                                    val isFirst = index == 0
                                    val isLast = index == state.rows.lastIndex
                                    // Same fixed box size and modifier shape for both
                                    // chevrons regardless of enabled state, so a
                                    // conditionally-present clickable modifier can never
                                    // give one of them a different bounding box than the
                                    // other and throw off their vertical alignment.
                                    val chevronSize = 1.75f
                                    // The SDK's UP/DOWN icon glyphs are built by rotating
                                    // one asymmetric shape ±90° around its viewport center,
                                    // so neither glyph is actually centered within its own
                                    // box - each sits ~18.1% of the box size off-center, in
                                    // opposite directions. Compensate so the visible glyphs
                                    // land back on the same line instead of the boxes.
                                    val glyphOffset = (chevronSize * 0.1814f).gridUnitsAsDp()
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 0.5f.gridUnitsAsDp())
                                            .size(chevronSize.gridUnitsAsDp())
                                            .alpha(if (isFirst) 0.3f else 1f)
                                            .lightClickable(enabled = !isFirst) { viewModel.moveUp(row.id) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LightIcon(
                                            icon = LightIcons.UP,
                                            size = chevronSize,
                                            modifier = Modifier.offset(y = glyphOffset),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(chevronSize.gridUnitsAsDp())
                                            .alpha(if (isLast) 0.3f else 1f)
                                            .lightClickable(enabled = !isLast) { viewModel.moveDown(row.id) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LightIcon(
                                            icon = LightIcons.DOWN,
                                            size = chevronSize,
                                            modifier = Modifier.offset(y = -glyphOffset),
                                        )
                                    }
                                } else {
                                    LightText(text = row.timeText, variant = LightTextVariant.Subheading)
                                }
                            }
                        }
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.ADD,
                            onClick = {
                                navigateTo(
                                    screenFactory = { LocationMapScreen(it, repo) },
                                    resultCallback = { id -> if (id != null) viewModel.reload() },
                                )
                            },
                            contentDescription = "Add a location",
                        ),
                    ),
                )
            }
        }
    }
}
