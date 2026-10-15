package schedule

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleParserTest {
    private fun failed(line: String): RejectionReason {
        val failure = assertIs<ParseResult.Failure>(parseScheduleLine(7L, line))
        assertEquals(7L, failure.lineNumber)
        return failure.reason
    }

    @Test
    fun parsesAllKindsAndPreservesTeacherOrder() {
        val inputs = listOf(
            "2026-10-05|LECTURE|L Topic|Room 1|Ivanov",
            "2026-10-05|PRACTICE|P Topic|Room 2|Petrov",
            "2026-10-05|EXAM|E Topic|Room 3|Petrov,Ivanov",
            "2026-10-05|CANCELLED|C Topic|-|-",
        )
        val types = listOf(SessionType.LECTURE, SessionType.PRACTICE, SessionType.EXAM, SessionType.CANCELLED)
        for ((index, line) in inputs.withIndex()) {
            val result = assertIs<ParseResult.Success>(parseScheduleLine(index + 1L, line))
            assertEquals(types[index], sessionType(result.session))
        }
        val exam = assertIs<Exam>(assertIs<ParseResult.Success>(parseScheduleLine(1, inputs[2])).session)
        assertEquals(listOf("Petrov", "Ivanov"), exam.teachers)
    }

    @Test
    fun checksSeparatorFieldsEmptyDateAndTypeInThatPriority() {
        assertEquals(RejectionReason.InvalidSeparator, failed(""))
        assertEquals(RejectionReason.InvalidSeparator, failed("plain text"))
        assertEquals(
            RejectionReason.WrongFieldCount(6),
            failed("2026-10-05|LECTURE|Topic|Room|Ivanov|"),
        )
        assertEquals(
            RejectionReason.EmptyField("date"),
            failed(" |SEMINAR|Topic|Room|Ivanov"),
        )
        assertEquals(
            RejectionReason.EmptyField("teachers"),
            failed("2026-10-05|LECTURE|Topic|Room|"),
        )
        assertEquals(
//            RejectionReason.InvalidDate(InvalidDateType.DATE, "2026-13-45"),
            RejectionReason.InvalidDate("2026-13-45"),
            failed("2026-13-45|SEMINAR|Topic|Room|Ivanov"),
        )
//        for ((type, date) in listOf(InvalidDateType.YEAR to "0000-01-01", InvalidDateType.DATE to "10000-01-01",
//            InvalidDateType.DATE to "2026-02-29", InvalidDateType.FORMAT to "2026-2-01",
//            InvalidDateType.DATE to "2026-01-99")) {
        for (date in listOf("0000-01-01", "10000-01-01", "2026-02-29", "2026-2-01", "2026-01-99")) {
            assertEquals(
//                RejectionReason.InvalidDate(type, date),
                RejectionReason.InvalidDate(date),
                failed("$date|LECTURE|Topic|Room|Ivanov"),
            )
        }
        assertEquals(
            RejectionReason.UnknownType("SEMINAR"),
            failed("2026-10-05|SEMINAR|Topic|Room|Ivanov"),
        )
    }

    @Test
    fun examCountPrecedesDomainIssuesButDuplicatesRemainSeparate() {
        assertEquals(
            RejectionReason.ExamTeacherCount(1),
            failed("2026-10-05|EXAM|Topic|Room|Ivanov,-"),
        )
        assertEquals(
            RejectionReason.ExamTeacherCount(1),
            failed("2026-10-05|EXAM|Topic|Room|Ivanov,"),
        )
        assertEquals(
            RejectionReason.DuplicateTeacher("Ivanov"),
            failed("2026-10-05|EXAM|Topic|Room|Ivanov,Ivanov"),
        )
    }

    @Test
    fun ordersDomainFailuresAcrossFieldsAndKinds() {
        assertEquals(
            RejectionReason.ControlCharacter("location"),
            failed("2026-10-05|LECTURE| Topic|Room\u0001|Ivanov"),
        )
        assertEquals(
            RejectionReason.ControlCharacter("topic"),
            failed("2026-10-05|LECTURE|T\u0001opic|Room\u0002|Ivanov"),
        )
        assertEquals(
            RejectionReason.SurroundingWhitespace("topic"),
            failed("2026-10-05|LECTURE| Topic| Room|Ivanov"),
        )
        assertEquals(
            RejectionReason.SurroundingWhitespace("teachers"),
            failed("2026-10-05|LECTURE|Topic|Room|Ivanov, Petrov"),
        )
        assertEquals(
            RejectionReason.InvalidCancellationField("location", "Room"),
            failed("2026-10-05|CANCELLED|Topic|Room|Ivanov"),
        )
        assertEquals(
            RejectionReason.InvalidCancellationField("teachers", "Ivanov"),
            failed("2026-10-05|CANCELLED|Topic|-|Ivanov"),
        )
        assertEquals(
            RejectionReason.InvalidLocation("-"),
            failed("2026-10-05|LECTURE|Topic|-|Ivanov"),
        )
        assertEquals(
            RejectionReason.InvalidTeacher(2, ""),
            failed("2026-10-05|LECTURE|Topic|Room|Ivanov,"),
        )
        assertEquals(
            RejectionReason.InvalidTeacher(3, "-"),
            failed("2026-10-05|EXAM|Topic|Room|Ivanov,Petrov,-"),
        )
        assertEquals(
            RejectionReason.InvalidTeacher(3, ""),
            failed("2026-10-05|EXAM|Topic|Room|Ivanov,Ivanov,"),
        )
    }

    @Test
    fun escapesEveryNonAsciiAndControlAsUtf16CodeUnits() {
        val value = "A\\\"\tЖ${String(Character.toChars(0x1F600))}"
        assertEquals("A\\\\\\\"\\u0009\\u0416\\uD83D\\uDE00", escapeDiagnostic(value))
        assertEquals(
            "line 7: unknown session type \"SEMINAR\"",
            formatFailure(ParseResult.Failure(7, RejectionReason.UnknownType("SEMINAR"))),
        )
        assertEquals(
            "line 7: invalid date \"2026-13-45\"",
//            formatFailure(ParseResult.Failure(7, RejectionReason.InvalidDate(InvalidDateType.DATE, "2026-13-45"))),
            formatFailure(ParseResult.Failure(7, RejectionReason.InvalidDate("2026-13-45"))),
        )
        assertEquals(
            "line 7: type \"EXAM\" requires at least 2 teachers, got 1",
            formatFailure(ParseResult.Failure(7, RejectionReason.ExamTeacherCount(1))),
        )
        assertEquals(
            "line 7: got 6 fields, expected 5",
            formatFailure(ParseResult.Failure(7, RejectionReason.WrongFieldCount(6))),
        )
    }
}
