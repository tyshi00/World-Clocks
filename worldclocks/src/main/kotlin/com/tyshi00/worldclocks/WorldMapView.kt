package com.tyshi00.worldclocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource

/**
 * The bundled map artwork ([R.drawable.world_map]) covers this longitude/
 * latitude range. These were verified against real landmarks rather than
 * guessed from the canvas aspect ratio: Greenland's northern tip (Cape
 * Morris Jesup / Kaffeklubben Island, ~83.6°N — one of the northernmost
 * points of land on Earth) measures at pixel row 53 of the artwork, and
 * Cape Horn (South America's southern tip, ~-55.9°) at row 936. Fitting a
 * line through those two measured points and capping the north edge at
 * the physical pole gives this range.
 */
private const val MAP_MIN_LON = -180.0
private const val MAP_MAX_LON = 180.0
private const val MAP_MIN_LAT = -66.4
private const val MAP_MAX_LAT = 90.0
private const val FULL_SPAN_LON = MAP_MAX_LON - MAP_MIN_LON
private const val FULL_SPAN_LAT = MAP_MAX_LAT - MAP_MIN_LAT

/**
 * Describes what slice of the world is currently visible, in degrees.
 * Only longitude span is stored — latitude span is always *derived* from
 * the box's actual on-screen aspect ratio at draw time (see [WorldMapView]),
 * so degrees-per-pixel is guaranteed identical on both axes. That's what
 * keeps the map from ever stretching: picking spanLon and spanLat
 * independently is exactly what caused continents to look squished before.
 */
data class MapViewport(
    val centerLon: Double,
    val centerLat: Double,
    val spanLon: Double,
) {
    companion object {
        /** The whole map artwork. Longitude span is capped as high as the box allows without stretching. */
        val WORLD = MapViewport(centerLon = 0.0, centerLat = 0.0, spanLon = FULL_SPAN_LON)
    }
}

/**
 * Draws the bundled world map artwork for the given [viewport], tinted to
 * [mapTintColor], with a faint reference grid. Purely decorative - it
 * doesn't attempt to pinpoint any specific searched location. The artwork
 * is a simplified, traced illustration rather than survey-grade geographic
 * data, so a marker claiming to sit exactly on a given coordinate would be
 * visibly, unavoidably a bit off; the place name/coordinates/time in the
 * text panel below it are the actually-accurate confirmation of the
 * result, not the map.
 */
@Composable
fun WorldMapView(
    viewport: MapViewport,
    mapTintColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    val mapPainter = painterResource(R.drawable.world_map)
    val tintFilter = remember(mapTintColor) { ColorFilter.tint(mapTintColor, BlendMode.SrcIn) }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Latitude span is derived from the box's own aspect ratio so
            // degrees-per-pixel is identical horizontally and vertically —
            // never chosen independently, which is what distorts shapes.
            var spanLon = viewport.spanLon
            var spanLat = spanLon * (h / w)
            if (spanLat > FULL_SPAN_LAT) {
                // Requesting more vertical coverage than the artwork has
                // (e.g. the full-world view on a tall/portrait screen).
                // Rather than letterbox with blank bars, zoom in just
                // enough to use all available map content, cropping some
                // longitude instead of stretching or leaving gaps.
                spanLat = FULL_SPAN_LAT
                spanLon = spanLat * (w / h)
            }

            // Keep the whole viewport within the artwork's real coverage —
            // shifts the center near the poles rather than showing blank
            // space past the top/bottom edge of the source image.
            val halfLat = spanLat / 2.0
            val centerLat = viewport.centerLat.coerceIn(MAP_MIN_LAT + halfLat, MAP_MAX_LAT - halfLat)

            val minLon = viewport.centerLon - spanLon / 2.0
            val maxLat = centerLat + halfLat

            fun project(lon: Double, lat: Double): Offset {
                val x = ((lon - minLon) / spanLon) * w
                val y = ((maxLat - lat) / spanLat) * h
                return Offset(x.toFloat(), y.toFloat())
            }

            // Draw the map image at a size derived from the *same*
            // pixels-per-degree as project() above, so it lines up exactly
            // with the grid instead of using separate math.
            val pixelsPerDegree = w / spanLon
            val imageDrawWidth = (FULL_SPAN_LON * pixelsPerDegree).toFloat()
            val imageDrawHeight = (FULL_SPAN_LAT * pixelsPerDegree).toFloat()
            val imageOrigin = project(MAP_MIN_LON, MAP_MAX_LAT)

            translate(left = imageOrigin.x, top = imageOrigin.y) {
                with(mapPainter) {
                    draw(size = Size(imageDrawWidth, imageDrawHeight), colorFilter = tintFilter)
                }
            }

            // Longitude/latitude reference grid, drawn on top of the map.
            var lonLine = -180.0
            while (lonLine <= 180.0) {
                drawLine(gridColor, project(lonLine, -80.0), project(lonLine, 80.0), strokeWidth = 1f)
                lonLine += 30.0
            }
            var latLine = -60.0
            while (latLine <= 80.0) {
                drawLine(gridColor, project(-180.0, latLine), project(180.0, latLine), strokeWidth = 1f)
                latLine += 30.0
            }
        }
    }
}
