package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModelAndRepositoryTest {
    private val day = LocalDate.of(2026, 10, 5)

    @Test
    fun createsEveryTypeWithOnlyItsOwnProperties() {
        assertIs<Lecture>(Lecture(day, "Topic L", "Room 1", listOf("Ivanov")))
        assertIs<Practice>(Practice(day, "Topic P", "Room 2", listOf("Petrov")))
        assertIs<Exam>(Exam(day, "Topic E", "Room 3", listOf("Ivanov", "Petrov")))
        val cancelled: Session = Cancelled(day, "Cancelled lecture")
        assertIs<Cancelled>(cancelled)
        assertEquals(SessionType.CANCELLED, sessionType(cancelled))
        assertEquals("2026-10-05 | CANCELLED | Cancelled lecture | - | -", formatSession(cancelled, true))
    }

    @Test
    fun constructorsRejectInvalidTopicsYearsAndPlaces() {
        val invalidDates = listOf(LocalDate.of(0, 1, 1), LocalDate.of(10000, 1, 1))
        for (date in invalidDates) {
            assertFailsWith<IllegalArgumentException> { Cancelled(date, "Topic") }
            assertFailsWith<IllegalArgumentException> { Lecture(date, "Topic", "Room", listOf("Ivanov")) }
        }
        for (topic in listOf("", " ", " Topic", "Topic ", "Top\u0001ic", "\tTopic")) {
            assertFailsWith<IllegalArgumentException> { Cancelled(day, topic) }
        }
        for (place in listOf("", " ", "-", " Room", "Room ", "Ro\u0000om")) {
            assertFailsWith<IllegalArgumentException> { Practice(day, "Topic", place, listOf("Ivanov")) }
        }
    }

    @Test
    fun constructorsRejectInsufficientInvalidAndDuplicateTeachers() {
        assertFailsWith<IllegalArgumentException> { Lecture(day, "Topic", "Room", emptyList()) }
        assertFailsWith<IllegalArgumentException> { Practice(day, "Topic", "Room", listOf("-")) }
        assertFailsWith<IllegalArgumentException> { Exam(day, "Topic", "Room", listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> {
            Exam(day, "Topic", "Room", listOf("Ivanov", "Ivanov"))
        }
        for (name in listOf("", " ", " Ivanov", "Ivanov ", "Iva\u0001nov", "-")) {
            assertFailsWith<IllegalArgumentException> { Lecture(day, "Topic", "Room", listOf(name)) }
        }
        val exam = Exam(day, "Topic", "Room", listOf("Ivanov", "ivanov"))
        assertEquals(listOf("Ivanov", "ivanov"), exam.teachers)
    }

    @Test
    fun protectsOriginalAndExposedTeacherLists() {
        val original = mutableListOf("Ivanov", "Petrov")
        val exam = Exam(day, "Topic", "Room", original)
        original.clear()
        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
        assertFailsWith<UnsupportedOperationException> {
            (exam.teachers as MutableList<String>).clear()
        }
        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
    }

    @Test
    fun repositoryKeepsOrderDuplicatesAndDetachedUnmodifiableSnapshots() {
        val repo = InMemorySessionRepository()
        val lecture = Lecture(day, "Topic", "Room", listOf("Ivanov"))
        val first = repo.all()
        repo.addAll(listOf(lecture, lecture))
        assertTrue(first.isEmpty())
        val snapshot = repo.all()
        repo.add(Cancelled(day, "Cancelled"))
        assertEquals(listOf(lecture, lecture), snapshot)
        assertEquals(3, repo.all().size)
        assertFailsWith<UnsupportedOperationException> {
            (snapshot as MutableList<Session>).clear()
        }
        assertEquals(3, repo.all().size)
    }

    @Test
    fun auditingAccountsForAddAndAddAllExactlyOnce() {
        val repo = AuditedSessionRepository(InMemorySessionRepository())
        val lecture = Lecture(day, "Topic", "Room", listOf("Ivanov"))
        val eventSnapshot = repo.auditEvents
        repo.add(lecture)
        repo.addAll(listOf(lecture, lecture))
        repo.addAll(emptyList())
        val currentEvents = repo.auditEvents
        repo.all()
        assertEquals(3, repo.addedCount)
        assertEquals(3, repo.all().size)
        assertEquals(
            listOf(AuditEvent("add", 1), AuditEvent("addAll", 2), AuditEvent("addAll", 0)),
            currentEvents,
        )
        assertTrue(eventSnapshot.isEmpty())
        assertFailsWith<UnsupportedOperationException> {
            (currentEvents as MutableList<AuditEvent>).clear()
        }
        assertEquals(3, repo.auditEvents.size)
    }

    @Test
    fun failedDelegateAdditionDoesNotProduceAuditEvent() {
        val rejecting = object : SessionRepository {
            override fun add(session: Session): Unit = error("reject")
            override fun addAll(sessions: List<Session>): Unit = error("reject")
            override fun all(): List<Session> = emptyList()
        }
        val repo = AuditedSessionRepository(rejecting)
        val sample = Cancelled(day, "Topic")
        assertFailsWith<IllegalStateException> { repo.add(sample) }
        assertFailsWith<IllegalStateException> { repo.addAll(listOf(sample)) }
        assertEquals(0, repo.addedCount)
        assertTrue(repo.auditEvents.isEmpty())
    }
}
