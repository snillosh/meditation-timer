package uk.co.meditationtimer

import java.time.*

interface Clock { fun elapsed(): Long; fun wall(): Long; fun boot(): Int }
data class Session(val id: String, val duration: Long, val remaining: Long, val deadline: Long,
    val boot: Int, val state: String = "running", val test: Boolean = false, val expectedWall: Long = 0,
    val generation: Int = 0, val kind: String = MEDITATION, val accrued: Long = 0, val started: Long = 0) {
    val open: Boolean get() = kind == MINDFUL_OPEN
    fun activeMillis(now: Long) = accrued + if (state == "running") (now - started).coerceAtLeast(0) else 0
    fun left(now: Long) = if (state == "running") (deadline - now).coerceIn(0, duration) else remaining
    fun pause(now: Long) = if(open) copy(accrued=activeMillis(now), state="paused", generation=generation+1) else copy(remaining = left(now), state = "paused", generation = generation + 1)
    fun resume(now: Long, wall: Long) = if(open) copy(started=now, state="running", generation=generation+1) else copy(deadline = now + remaining, expectedWall = wall + remaining,
        state = "running", generation = generation + 1)
    fun due(now: Long, currentBoot: Int, token: String, revision: Int) = !open && state == "running" && boot == currentBoot && id == token && generation == revision && now >= deadline
}
data class Completed(val id: String, val millis: Long, val duration: Long, val date: LocalDate, val zone: String, val kind: String = MEDITATION)
data class Stats(val count: Int, val minutes: Long, val current: Int, val longest: Int, val week: List<Long>)
fun statistics(rows: List<Completed>, today: LocalDate): Stats {
    val days = rows.map { it.date }.toSet().sorted()
    var longest = 0; var run = 0; var previous: LocalDate? = null
    days.forEach { day -> run = if (previous?.plusDays(1) == day) run + 1 else 1; longest = maxOf(longest, run); previous = day }
    val set = days.toSet(); var cursor = if (today in set) today else today.minusDays(1); var current = 0
    while (cursor in set) { current++; cursor = cursor.minusDays(1) }
    val week = (6 downTo 0).map { n -> rows.filter { it.date == today.minusDays(n.toLong()) }.sumOf { it.duration } / 60000 }
    return Stats(rows.size, rows.sumOf { it.duration } / 60000, current, longest, week)
}
fun nextReminder(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    var next = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
    if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
    return next
}

/** Monday-first rows, padded with empty cells so neighbouring months never look selectable. */
fun calendarWeeks(month: YearMonth): List<List<LocalDate?>> {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = List<LocalDate?>(offset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    return (cells + List<LocalDate?>((7 - cells.size % 7) % 7) { null }).chunked(7)
}

val DEFAULT_DURATION_PRESETS = listOf(5, 10, 15, 20, 30)

/** Preserve the user's order; duplicates and invalid entries are errors rather than silently removed. */
fun parseDurationPresets(text: String): List<Int>? {
    val values = text.split(',').map { it.trim().toIntOrNull() ?: return null }
    return values.takeIf { it.size in 1..8 && it.distinct().size == it.size && it.all { n -> n in 1..120 } }
}
