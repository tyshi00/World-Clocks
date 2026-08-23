package com.tyshi00.worldclocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
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
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

/** A geocoded result, not yet saved. */
data class FoundLocation(
    val city: String,
    val region: String?,
    val country: String?,
    val timeZoneId: String,
    val latitude: Double,
    val longitude: Double,
) {
    val fullName: String
        get() = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            region?.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() },
        ).joinToString(", ")
}

sealed class LocationMapMode {
    data object SearchInput : LocationMapMode()
    data object Searching : LocationMapMode()
    data class Results(val candidates: List<FoundLocation>) : LocationMapMode()
    data class Found(val hit: FoundLocation) : LocationMapMode()
    data class Error(val message: String) : LocationMapMode()
}

data class LocationMapState(
    val mode: LocationMapMode = LocationMapMode.SearchInput,
    // Bumped every time we return to the search editor so LightTextInputEditor
    // treats it as a fresh keyboard session instead of reusing stale state.
    val searchSession: Int = 0,
)

class LocationMapViewModel(private val repo: WorldClocksRepository) : LightViewModel<Long?>() {

    private val api = GeocodingApi()

    private val _state = MutableStateFlow(LocationMapState())
    val state: StateFlow<LocationMapState> = _state.asStateFlow()

    fun submitSearch(rawQuery: CharSequence) {
        val query = rawQuery.toString().trim()
        if (query.isEmpty()) return
        _state.value = _state.value.copy(mode = LocationMapMode.Searching)
        viewModelScope.launch(Dispatchers.IO) {
            val result = api.search(query)
            result.fold(
                onSuccess = { hits ->
                    val candidates = hits.map { hit ->
                        FoundLocation(
                            city = hit.name,
                            region = hit.admin1,
                            country = hit.country,
                            timeZoneId = hit.timezone ?: ZoneId.systemDefault().id,
                            latitude = hit.latitude,
                            longitude = hit.longitude,
                        )
                    }
                    _state.value = _state.value.copy(
                        mode = if (candidates.size == 1) {
                            LocationMapMode.Found(candidates.first())
                        } else {
                            LocationMapMode.Results(candidates)
                        },
                    )
                },
                onFailure = { error ->
                    val message = when (error) {
                        is LocationNotFoundException ->
                            "No location found for that search. Try a different spelling or a nearby larger city."
                        else ->
                            "Couldn't reach the location service. Check your connection and try again."
                    }
                    _state.value = _state.value.copy(mode = LocationMapMode.Error(message))
                },
            )
        }
    }

    fun selectResult(candidate: FoundLocation) {
        _state.value = _state.value.copy(mode = LocationMapMode.Found(candidate))
    }

    fun searchAgain() {
        _state.value = _state.value.copy(
            mode = LocationMapMode.SearchInput,
            searchSession = _state.value.searchSession + 1,
        )
    }

    fun saveAndReturn(label: String, found: FoundLocation, onSaved: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanLabel = label.trim().ifEmpty { found.city }
            val id = repo.addLocation(
                label = cleanLabel,
                city = found.city,
                region = found.region,
                country = found.country,
                timeZoneId = found.timeZoneId,
                latitude = found.latitude,
                longitude = found.longitude,
            )
            withContext(Dispatchers.Main) { onSaved(id) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        api.close()
    }
}

class LocationMapScreen(
    sealedActivity: SealedLightActivity,
    private val repo: WorldClocksRepository,
) : LightScreen<Long?, LocationMapViewModel>(sealedActivity) {

    // Combined with searchSession below to guarantee a genuinely unique
    // LightTextInputEditor key per screen instance, not just per "search
    // again" within one instance — see LabelLocationScreen for why a
    // colliding key causes stale editor/keyboard state.
    private val instanceSeed = System.nanoTime()

    override val viewModelClass: Class<LocationMapViewModel>
        get() = LocationMapViewModel::class.java

    override fun createViewModel() = LocationMapViewModel(repo)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val textFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            when (val mode = state.mode) {
                is LocationMapMode.SearchInput -> {
                    LightTextInputEditor(
                        title = "Search a place",
                        editorKey = "$instanceSeed-${state.searchSession}",
                        state = textFieldState,
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        onSubmit = viewModel::submitSearch,
                        onBack = { goBack(null) },
                        submitIcon = LightIcons.SEARCH,
                        showBackButton = true,
                    )
                }

                is LocationMapMode.Searching -> {
                    MapScaffold(
                        title = "Add Location",
                        onBack = { goBack(null) },
                        onSearchAgain = { viewModel.searchAgain() },
                        viewport = MapViewport.WORLD,
                    ) {
                        InfoPanel {
                            LightText(text = "Searching…", variant = LightTextVariant.Copy, align = TextAlign.Center)
                        }
                    }
                }

                is LocationMapMode.Results -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightThemeTokens.colors.background),
                    ) {
                        LightTopBar(
                            leftButton = LightBarButton.LightIcon(
                                icon = LightIcons.BACK,
                                onClick = { viewModel.searchAgain() },
                            ),
                            center = LightTopBarCenter.Text("Choose a Location"),
                            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                        )
                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                        ) {
                            mode.candidates.forEach { candidate ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable { viewModel.selectResult(candidate) }
                                        .padding(vertical = 0.75f.gridUnitsAsDp()),
                                ) {
                                    LightText(text = candidate.city, variant = LightTextVariant.Copy)
                                    LightText(
                                        text = listOfNotNull(candidate.region, candidate.country)
                                            .joinToString(", "),
                                        variant = LightTextVariant.Fine,
                                        lighten = true,
                                    )
                                }
                            }
                        }
                        LightBottomBar(items = listOf())
                    }
                }

                is LocationMapMode.Found -> {
                    val hit = mode.hit
                    val zone = runCatching { ZoneId.of(hit.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
                    val now = ZonedDateTime.now(zone)
                    MapScaffold(
                        title = "Add Location",
                        onBack = { goBack(null) },
                        onSearchAgain = { viewModel.searchAgain() },
                        viewport = MapViewport.WORLD,
                        bottomBar = {
                            LightBottomBar(
                                items = listOf(
                                    LightBarButton.Text(
                                        text = "CONFIRM",
                                        onClick = {
                                            navigateTo(
                                                screenFactory = { LabelLocationScreen(it, hit.city) },
                                                resultCallback = { label ->
                                                    if (label != null) {
                                                        viewModel.saveAndReturn(label, hit) { newId -> goBack(newId) }
                                                    }
                                                },
                                            )
                                        },
                                    ),
                                ),
                            )
                        },
                    ) {
                        InfoPanel {
                            LightText(
                                text = hit.fullName,
                                variant = LightTextVariant.Heading,
                                align = TextAlign.Center,
                            )
                            LightText(
                                text = "%.2f, %.2f".format(hit.latitude, hit.longitude),
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                align = TextAlign.Center,
                            )
                            LightText(
                                text = "${ClockFormatting.time(now, FormatPreferencesHolder.timeFormat)}  " +
                                    "(${ClockFormatting.gmtOffsetLabel(now)})",
                                variant = LightTextVariant.Subheading,
                                align = TextAlign.Center,
                            )
                        }
                    }
                }

                is LocationMapMode.Error -> {
                    MapScaffold(
                        title = "Add Location",
                        onBack = { goBack(null) },
                        onSearchAgain = { viewModel.searchAgain() },
                        viewport = MapViewport.WORLD,
                    ) {
                        InfoPanel {
                            LightText(text = mode.message, variant = LightTextVariant.Copy, align = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
