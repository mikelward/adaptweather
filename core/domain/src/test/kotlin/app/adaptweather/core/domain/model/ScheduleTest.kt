package app.adaptweather.core.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleTest {
    private val london = ZoneId.of("Europe/London")
    private val sydney = ZoneId.of("Australia/Sydney")

    @Test
    fun `requires at least one day`() {
        shouldThrow<IllegalArgumentException> {
            Schedule(LocalTime.of(7, 0), emptySet(), london)
        }
    }

    @Test
    fun `next occurrence is later today when now is before scheduled time`() {
        val schedule = Schedule(LocalTime.of(7, 0), Schedule.EVERY_DAY, london)
        val now = LocalDateTime.of(2026, 4, 25, 6, 30).atZone(london).toInstant()

        val next = schedule.nextOccurrenceAfter(now)

        next shouldBe LocalDateTime.of(2026, 4, 25, 7, 0).atZone(london).toInstant()
    }

    @Test
    fun `next occurrence is tomorrow when now is past scheduled time`() {
        val schedule = Schedule(LocalTime.of(7, 0), Schedule.EVERY_DAY, london)
        val now = LocalDateTime.of(2026, 4, 25, 8, 0).atZone(london).toInstant()

        val next = schedule.nextOccurrenceAfter(now)

        next shouldBe LocalDateTime.of(2026, 4, 26, 7, 0).atZone(london).toInstant()
    }

    @Test
    fun `next occurrence skips disallowed days`() {
        // Weekdays only.
        val schedule = Schedule(
            time = LocalTime.of(7, 0),
            days = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ),
            zoneId = london,
        )
        // Saturday 2026-04-25 at 08:00.
        val now = LocalDateTime.of(2026, 4, 25, 8, 0).atZone(london).toInstant()

        val next = schedule.nextOccurrenceAfter(now)

        // Monday 2026-04-27 at 07:00.
        next shouldBe LocalDateTime.of(2026, 4, 27, 7, 0).atZone(london).toInstant()
        next.atZone(london).dayOfWeek shouldBe DayOfWeek.MONDAY
    }

    @Test
    fun `wall-clock time is preserved across DST spring forward in London`() {
        val schedule = Schedule(LocalTime.of(7, 0), Schedule.EVERY_DAY, london)
        // 2026-03-28 22:00 London (still GMT). DST starts 2026-03-29 at 01:00 -> 02:00.
        val saturdayEvening = LocalDateTime.of(2026, 3, 28, 22, 0).atZone(london).toInstant()

        val next = schedule.nextOccurrenceAfter(saturdayEvening)

        // Sunday 7am BST should be 06:00 UTC, not 07:00 UTC.
        val nextLocal = next.atZone(london).toLocalDateTime()
        nextLocal shouldBe LocalDateTime.of(2026, 3, 29, 7, 0)
    }

    @Test
    fun `wall-clock time is preserved across DST fall back in London`() {
        val schedule = Schedule(LocalTime.of(7, 0), Schedule.EVERY_DAY, london)
        // 2026-10-24 22:00 London (BST). DST ends 2026-10-25 at 02:00 -> 01:00.
        val saturdayEvening = LocalDateTime.of(2026, 10, 24, 22, 0).atZone(london).toInstant()

        val next = schedule.nextOccurrenceAfter(saturdayEvening)

        val nextLocal = next.atZone(london).toLocalDateTime()
        nextLocal shouldBe LocalDateTime.of(2026, 10, 25, 7, 0)
    }

    @Test
    fun `next occurrence respects zone independent of system default`() {
        val sydneySchedule = Schedule(LocalTime.of(7, 0), Schedule.EVERY_DAY, sydney)
        val nowInSydney = LocalDateTime.of(2026, 4, 25, 6, 0).atZone(sydney).toInstant()

        val next = sydneySchedule.nextOccurrenceAfter(nowInSydney)

        next.atZone(sydney).toLocalDateTime() shouldBe LocalDateTime.of(2026, 4, 25, 7, 0)
    }

    @Test
    fun `default has 7am every day`() {
        val schedule = Schedule.default(london)
        schedule.time shouldBe LocalTime.of(7, 0)
        schedule.days shouldBe Schedule.EVERY_DAY
    }

    @Test
    fun `default uses provided zone`() {
        Schedule.default(sydney).zoneId shouldBe sydney
    }

    @Test
    fun `single-day schedule wraps to next week when now equals fire time`() {
        val schedule = Schedule(LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY), london)
        // Monday 2026-04-27 at exactly 07:00 -> not strictly after, so next is following Monday.
        val now = LocalDateTime.of(2026, 4, 27, 7, 0).atZone(london).toInstant()

        val next = schedule.nextOccurrenceAfter(now)

        val expected = LocalDate.of(2026, 5, 4).atTime(7, 0).atZone(london).toInstant()
        next shouldBe expected
    }
}
