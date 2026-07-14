package com.tyshi00.worldclocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * Full-screen layout shared by every mode of [LocationMapScreen]: a top bar,
 * the flat map filling the remaining space, and an optional bottom bar.
 * [content] is layered on top of the map (see [InfoPanel]) so search state,
 * results, or errors can be shown without leaving the map screen.
 */
@Composable
fun MapScaffold(
    title: String,
    onBack: () -> Unit,
    onSearchAgain: () -> Unit,
    viewport: MapViewport,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LightThemeTokens.colors
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(title),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SEARCH,
                onClick = onSearchAgain,
                contentDescription = "Search again",
            ),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WorldMapView(
                viewport = viewport,
                mapTintColor = colors.content.copy(alpha = 0.7f),
                gridColor = colors.content.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxSize(),
            )
            content()
        }

        bottomBar?.invoke() ?: LightBottomBar(items = listOf())
    }
}

/**
 * A solid background panel anchored to the bottom of the map so any text
 * placed inside it stays fully legible regardless of what's drawn behind it
 * on the map — continents, grid lines, or open ocean.
 */
@Composable
fun BoxScope.InfoPanel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LightThemeTokens.colors
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
