package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SessionTransferTest {
    private val original = LocalDate.of(2026, 10, 5)
    private val next = LocalDate.of(2026, 10, 12)

    @Test
    fun movesEveryActiveKindWithoutChangingItsOriginal() {
        val sessions: List<Session> = listOf(
            Lecture(original, "Lecture topic", "Room 1", listOf("Ivanov")),
            Practice(original, "Practice topic", "Room 2", listOf("Petrov")),
            Exam(original, "Exam topic", "Room 3", listOf("Ivanov", "Petrov")),
        )
        for (session in sessions) {
            val result = assertIs<TransferResult.Moved>(transferSession(session, next))
            assertEquals(original, session.date)
            assertEquals(next, result.session.date)
            assertEquals(session.topic, result.session.topic)
            assertEquals(sessionType(session), sessionType(result.session))
            assertEquals(
                formatSession(session, true).substringAfter(" | "),
                formatSession(result.session, true).substringAfter(" | "),
            )
        }
    }

    @Test
    fun cancelledIsRefusedAndInvalidYearCannotBeUsed() {
        assertEquals(
            TransferResult.CancelledSession,
            transferSession(Cancelled(original, "Cancelled"), next),
        )
        assertFailsWith<IllegalArgumentException> {
            transferSession(Lecture(original, "Topic", "Room", listOf("Ivanov")), LocalDate.of(0, 1, 1))
        }
        assertFailsWith<IllegalArgumentException> {
            transferSession(Cancelled(original, "Topic"), LocalDate.of(10000, 1, 1))
        }
    }
}
