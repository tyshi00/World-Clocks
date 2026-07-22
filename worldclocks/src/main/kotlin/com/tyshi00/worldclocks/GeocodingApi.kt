package com.tyshi00.worldclocks

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

@Serializable
internal data class GeocodingSearchResponse(
    val results: List<GeocodingHit> = emptyList(),
)

@Serializable
internal data class GeocodingHit(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
    // Open-Meteo's geocoder returns an IANA time zone id directly, so no
    // second lookup against a separate time zone API is needed.
    val timezone: String? = null,
)

internal class LocationNotFoundException : Exception("Location not found.")

/**
 * Thin wrapper around Open-Meteo's free, keyless geocoding endpoint —
 * the same provider the official Weather example tool in this SDK uses,
 * so it's already known to work well within LightOS's network constraints.
 */
internal class GeocodingApi {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun search(query: String, count: Int = 10): Result<List<GeocodingHit>> = runCatching {
        val encoded = URLEncoder.encode(query.trim(), UTF_8.name())
        val response = client.get(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=$count",
        )
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw IllegalStateException("Geocoding HTTP ${response.status.value}: $body")
        }
        val parsed: GeocodingSearchResponse = response.body()
        val hits = parsed.results.filter { !it.timezone.isNullOrBlank() }
        if (hits.isEmpty()) throw LocationNotFoundException()
        hits
    }

    fun close() = client.close()
}
