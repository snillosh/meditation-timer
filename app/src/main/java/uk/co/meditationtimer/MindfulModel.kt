package uk.co.meditationtimer

import java.time.*
import kotlin.random.Random

const val MEDITATION = "meditation"
const val MINDFUL_TIMED = "mindful_timed"
const val MINDFUL_OPEN = "mindful_open"
val SESSION_KINDS = setOf(MEDITATION, MINDFUL_TIMED, MINDFUL_OPEN)

data class MindfulConfig(
    val enabled: Boolean = false,
    val activity: String = "timed",
    val durationMinutes: Int = 2,
    val schedule: String = "random",
    val days: Set<Int> = (1..7).toSet(),
    val fixedTimes: List<Int> = listOf(13 * 60),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val nudges: Int = 1,
    val seed: Long = 0
) {
    fun error(): String? = when {
        activity !in setOf("timed", "open", "prompt") -> "Choose an activity."
        activity == "timed" && durationMinutes !in 1..120 -> "Choose a duration from 1 to 120 minutes."
        schedule !in setOf("fixed", "random") -> "Choose a schedule."
        days.isEmpty() || days.any { it !in 1..7 } -> "Choose at least one weekday."
        schedule == "fixed" && (fixedTimes.size !in 1..8 || fixedTimes.distinct().size != fixedTimes.size || fixedTimes.any { it !in 0..1439 }) -> "Choose 1–8 different fixed times."
        schedule == "random" && (startMinute !in 0..1439 || endMinute !in 0..1439 || startMinute >= endMinute) -> "The window must start before it ends on the same day."
        schedule == "random" && nudges !in 1..8 -> "Choose 1–8 nudges per day."
        schedule == "random" && endMinute - startMinute < nudges * 30 -> "Allow at least 30 minutes of window per nudge."
        else -> null
    }
}

/** One random point in the middle half of each equal slot: well-spaced and stable across restart. */
fun mindfulTimes(config: MindfulConfig, date: LocalDate, zone: ZoneId): List<ZonedDateTime> {
    if (!config.enabled || config.error() != null || date.dayOfWeek.value !in config.days) return emptyList()
    val minutes = if (config.schedule == "fixed") config.fixedTimes else {
        val random = Random(config.seed xor date.toEpochDay())
        val width = config.endMinute - config.startMinute
        (0 until config.nudges).map { index ->
            val low = config.startMinute + width * index / config.nudges
            val high = config.startMinute + width * (index + 1) / config.nudges
            val inset = (high - low) / 4
            random.nextInt(low + inset, high - inset)
        }
    }
    return minutes.map { date.atTime(it / 60, it % 60).atZone(zone) }
        .filter { it.toLocalDate() == date && (config.schedule != "random" ||
            it.toLocalTime() >= LocalTime.of(config.startMinute / 60, config.startMinute % 60) &&
            it.toLocalTime() < LocalTime.of(config.endMinute / 60, config.endMinute % 60)) }
        .distinctBy { it.toInstant() }.sortedBy { it.toInstant() }
}

fun nextMindful(config: MindfulConfig, now: ZonedDateTime, lastSlot: Long = 0): ZonedDateTime? =
    (0L..7L).asSequence().flatMap { mindfulTimes(config, now.toLocalDate().plusDays(it), now.zone).asSequence() }
        .firstOrNull { it.isAfter(now) && it.toInstant().toEpochMilli() > lastSlot }

/** Drop stale/changed/out-of-window slots instead of catching up in a burst. */
fun mindfulDeliveryAllowed(config: MindfulConfig, due: Long, now: ZonedDateTime, lastSlot: Long, lastDelivery: Long): Boolean {
    val actual = now.toInstant().toEpochMilli()
    if (due <= lastSlot || actual < due || actual - due > 60 * 60 * 1000L) return false
    if (mindfulTimes(config, now.toLocalDate(), now.zone).none { it.toInstant().toEpochMilli() == due }) return false
    if (config.schedule == "random") {
        val minute = now.hour * 60 + now.minute
        if (minute !in config.startMinute until config.endMinute || actual - lastDelivery < 15 * 60000L) return false
    }
    return true
}

fun minuteLabel(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
fun parseFixedTimes(text: String): List<Int>? {
    val times = text.split(',').map {
        val parts = it.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        h * 60 + m
    }
    return times.takeIf { it.size in 1..8 && it.distinct().size == it.size }
}
