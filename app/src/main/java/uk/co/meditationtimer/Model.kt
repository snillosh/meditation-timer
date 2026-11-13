package uk.co.meditationtimer

import java.time.*

interface Clock { fun elapsed(): Long; fun wall(): Long; fun boot(): Int }
data class Session(val id: String, val duration: Long, val remaining: Long, val deadline: Long,
    val boot: Int, val state: String = "running", val test: Boolean = false, val expectedWall: Long = 0,
    val generation: Int = 0) {
    fun left(now: Long) = if (state == "running") (deadline - now).coerceIn(0, duration) else remaining
    fun pause(now: Long) = copy(remaining = left(now), state = "paused", generation = generation + 1)
    fun resume(now: Long, wall: Long) = copy(deadline = now + remaining, expectedWall = wall + remaining,
        state = "running", generation = generation + 1)
    fun due(now: Long, currentBoot: Int, token: String, revision: Int) = state == "running" && boot == currentBoot && id == token && generation == revision && now >= deadline
}
data class Completed(val id: String, val millis: Long, val duration: Long, val date: LocalDate, val zone: String)
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
