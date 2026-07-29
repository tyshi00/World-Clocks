package com.tyshi00.worldclocks

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

enum class TimeFormat(val label: String) {
    AM_PM("AM/PM"),
    HOUR_24("24-hour"),
}

// Ordered to match the sequence requested for the settings picker:
// yyyy/mm/dd, dd/mm/yyyy, mm/dd/yyyy.
enum class DateFormat(val label: String) {
    YMD("yyyy/mm/dd"),
    DMY("dd/mm/yyyy"),
    MDY("mm/dd/yyyy"),
}

enum class HomeLayout(val label: String) {
    CLASSIC("Classic"),
    SPLIT("Split view"),
}

/**
 * In-memory mirror of the two format preferences, refreshed whenever they're
 * loaded or changed. Several screens format a clock reading on every tick
 * (once a second on Home), so formatting can't itself be a suspend call that
 * hits the database each time — this is read synchronously from Compose
 * instead, the same pattern LightThemeController uses for the color scheme.
 */
object FormatPreferencesHolder {
    @Volatile var timeFormat: TimeFormat = TimeFormat.AM_PM
    @Volatile var dateFormat: DateFormat = DateFormat.MDY
    @Volatile var homeLayout: HomeLayout = HomeLayout.CLASSIC
}

/** Plain domain model mapped from [SavedLocationEntity] — screens work with this, never the entity directly. */
data class SavedLocation(
    val id: Long,
    val label: String,
    val city: String,
    val region: String?,
    val country: String?,
    val timeZoneId: String,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
) {
    val zoneId: ZoneId
        get() = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())

    /** "City, State/Province, Country" — any missing part is simply omitted. */
    fun fullPlaceName(): String = listOfNotNull(
        city.takeIf { it.isNotBlank() },
        region?.takeIf { it.isNotBlank() },
        country?.takeIf { it.isNotBlank() },
    ).joinToString(", ")
}

private fun SavedLocationEntity.toDomain() = SavedLocation(
    id = id,
    label = label,
    city = city,
    region = region,
    country = country,
    timeZoneId = timeZoneId,
    latitude = latitude,
    longitude = longitude,
    sortOrder = sortOrder,
)

/** All clock/date string formatting lives here so every screen renders identically. */
object ClockFormatting {

    fun time(zoned: ZonedDateTime, format: TimeFormat): String {
        val pattern = if (format == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
        return zoned.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun date(zoned: ZonedDateTime, format: DateFormat): String {
        val pattern = when (format) {
            DateFormat.YMD -> "yyyy/MM/dd"
            DateFormat.DMY -> "dd/MM/yyyy"
            DateFormat.MDY -> "MM/dd/yyyy"
        }
        return zoned.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun weekdayLabel(zoned: ZonedDateTime): String =
        zoned.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)

    /** "GMT+9", "GMT-5", "GMT+5:30" — includes minutes only when the offset isn't a whole hour. */
    fun gmtOffsetLabel(zoned: ZonedDateTime): String {
        val totalMinutes = zoned.offset.totalSeconds / 60
        val sign = if (totalMinutes >= 0) "+" else "-"
        val hours = abs(totalMinutes) / 60
        val minutes = abs(totalMinutes) % 60
        return if (minutes == 0) "GMT$sign$hours" else "GMT$sign$hours:${minutes.toString().padStart(2, '0')}"
    }

    /** e.g. "9h 30m ahead of you", "3h behind you", "Same time as you" — evaluated at [at], so it accounts for DST correctly. */
    fun differenceSummary(localZone: ZoneId, targetZone: ZoneId, at: Instant = Instant.now()): String {
        val localOffsetMinutes = localZone.rules.getOffset(at).totalSeconds / 60
        val targetOffsetMinutes = targetZone.rules.getOffset(at).totalSeconds / 60
        val diff = targetOffsetMinutes - localOffsetMinutes
        if (diff == 0) return "Same time as you"
        val direction = if (diff > 0) "ahead of you" else "behind you"
        val absDiff = abs(diff)
        val hours = absDiff / 60
        val minutes = absDiff % 60
        val magnitude = when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
        return "$magnitude $direction"
    }

    /** Calendar-day relationship of target vs. local — null when they fall on the same date. */
    fun dayOffsetNote(localZoned: ZonedDateTime, targetZoned: ZonedDateTime): String? {
        val diff = targetZoned.toLocalDate().toEpochDay() - localZoned.toLocalDate().toEpochDay()
        return when {
            diff > 0 -> "It's a day ahead there"
            diff < 0 -> "It's a day behind there"
            else -> null
        }
    }
}

class WorldClocksRepository(private val db: WorldClocksDatabase) {

    companion object {
        private const val PREF_TIME_FORMAT = "time_format"
        private const val PREF_DATE_FORMAT = "date_format"
        private const val PREF_HOME_LAYOUT = "home_layout"
        private const val PREF_INVERT_COLORS = "invert_colors"
        private const val PREF_COMPARE_TARGET_ID = "compare_target_id"

        @Volatile private var INSTANCE: WorldClocksRepository? = null

        fun getInstance(factory: () -> WorldClocksDatabase): WorldClocksRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorldClocksRepository(factory()).also { INSTANCE = it }
            }
    }

    // ── Saved locations ─────────────────────────────────────────────────

    suspend fun getSavedLocations(): List<SavedLocation> =
        db.savedLocationDao().getAll().map { it.toDomain() }

    suspend fun getSavedLocation(id: Long): SavedLocation? =
        db.savedLocationDao().getById(id)?.toDomain()

    suspend fun addLocation(
        label: String,
        city: String,
        region: String?,
        country: String?,
        timeZoneId: String,
        latitude: Double,
        longitude: Double,
    ): Long {
        val nextOrder = db.savedLocationDao().getMaxSortOrder() + 1
        return db.savedLocationDao().insert(
            SavedLocationEntity(
                label = label,
                city = city,
                region = region,
                country = country,
                timeZoneId = timeZoneId,
                latitude = latitude,
                longitude = longitude,
                sortOrder = nextOrder,
            ),
        )
    }

    suspend fun renameLocation(id: Long, newLabel: String) {
        val existing = db.savedLocationDao().getById(id) ?: return
        db.savedLocationDao().update(existing.copy(label = newLabel))
    }

    suspend fun deleteLocation(id: Long) {
        db.savedLocationDao().deleteById(id)
        if (getCompareTargetId() == id) setCompareTargetId(null)
    }

    /** Swaps sort order with the neighboring saved location — [direction] is -1 (move up) or +1 (move down). */
    suspend fun moveLocation(id: Long, direction: Int) {
        val all = db.savedLocationDao().getAll()
        val index = all.indexOfFirst { it.id == id }
        val swapIndex = index + direction
        if (index < 0 || swapIndex < 0 || swapIndex >= all.size) return
        val current = all[index]
        val neighbor = all[swapIndex]
        db.savedLocationDao().update(current.copy(sortOrder = neighbor.sortOrder))
        db.savedLocationDao().update(neighbor.copy(sortOrder = current.sortOrder))
    }

    // ── Preferences ─────────────────────────────────────────────────────

    suspend fun getTimeFormat(): TimeFormat {
        val raw = db.preferenceDao().get(PREF_TIME_FORMAT)?.value ?: return TimeFormat.AM_PM
        return TimeFormat.entries.firstOrNull { it.name == raw } ?: TimeFormat.AM_PM
    }

    suspend fun setTimeFormat(format: TimeFormat) {
        db.preferenceDao().set(PreferenceEntity(PREF_TIME_FORMAT, format.name))
    }

    suspend fun getDateFormat(): DateFormat {
        val raw = db.preferenceDao().get(PREF_DATE_FORMAT)?.value ?: return DateFormat.MDY
        return DateFormat.entries.firstOrNull { it.name == raw } ?: DateFormat.MDY
    }

    suspend fun setDateFormat(format: DateFormat) {
        db.preferenceDao().set(PreferenceEntity(PREF_DATE_FORMAT, format.name))
    }

    suspend fun getHomeLayout(): HomeLayout {
        val raw = db.preferenceDao().get(PREF_HOME_LAYOUT)?.value ?: return HomeLayout.CLASSIC
        return HomeLayout.entries.firstOrNull { it.name == raw } ?: HomeLayout.CLASSIC
    }

    suspend fun setHomeLayout(layout: HomeLayout) {
        db.preferenceDao().set(PreferenceEntity(PREF_HOME_LAYOUT, layout.name))
    }

    suspend fun getInvertColors(): Boolean =
        db.preferenceDao().get(PREF_INVERT_COLORS)?.value == "true"

    suspend fun setInvertColors(value: Boolean) {
        db.preferenceDao().set(PreferenceEntity(PREF_INVERT_COLORS, value.toString()))
    }

    suspend fun getCompareTargetId(): Long? =
        db.preferenceDao().get(PREF_COMPARE_TARGET_ID)?.value?.toLongOrNull()

    suspend fun setCompareTargetId(id: Long?) {
        if (id == null) db.preferenceDao().delete(PREF_COMPARE_TARGET_ID) else {
            db.preferenceDao().set(PreferenceEntity(PREF_COMPARE_TARGET_ID, id.toString()))
        }
    }

    /**
     * Clears saved locations (and the now-meaningless comparison target)
     * only. Display preferences like time/date format and invert-colors are
     * left alone — deleting your clock data shouldn't silently reset how the
     * app looks and reads, the same reasoning most settings screens use for
     * "reset data" vs. "reset settings".
     */
    suspend fun deleteAllSavedData() {
        db.savedLocationDao().deleteAll()
        db.preferenceDao().delete(PREF_COMPARE_TARGET_ID)
    }
}
