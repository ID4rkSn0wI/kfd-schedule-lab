package schedule

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Parsing returns structured information; it never prints or stores exceptions. */
sealed interface ParseResult {
    data class Success(val session: Session) : ParseResult
    data class Failure(val lineNumber: Long, val reason: RejectionReason) : ParseResult
}

/*
* made a sealed interface cause we should have a constant amount of rejection reason
* so we will have a settled exception's count in "when" statements
* also all the exceptions are inside RejectionReasons so others will be grateful for easier recognition of the source
*/
sealed interface RejectionReason {
    data object InvalidSeparator : RejectionReason
    data class WrongFieldCount(val actual: Int) : RejectionReason
    data class EmptyField(val field: String) : RejectionReason
//    data class InvalidDate(val type: InvalidDateType, val value: String) : RejectionReason
    data class InvalidDate(val value: String) : RejectionReason
    data class UnknownType(val value: String) : RejectionReason
    data class ExamTeacherCount(val actual: Int) : RejectionReason
    data class ControlCharacter(val field: String) : RejectionReason
    data class SurroundingWhitespace(val field: String) : RejectionReason
    data class InvalidCancellationField(val field: String, val value: String) : RejectionReason
    data class InvalidLocation(val value: String) : RejectionReason
    data class InvalidTeacher(val position: Int, val value: String) : RejectionReason
    data class DuplicateTeacher(val value: String) : RejectionReason
}

// wanted to differentiate date type errors
//enum class InvalidDateType {FORMAT, DATE, YEAR}

private val fieldNames = listOf("date", "type", "topic", "location", "teachers")
// a regular expression to check date format
private val strictDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

fun parseScheduleLine(lineNumber: Long, line: String): ParseResult {
    fun failure(reason: RejectionReason) = ParseResult.Failure(lineNumber, reason)

    // Each validation has a fixed priority. All made into small related groups.
    if ('|' !in line) return failure(RejectionReason.InvalidSeparator)
    val fields = line.split('|')
    if (fields.size != 5) return failure(RejectionReason.WrongFieldCount(fields.size))

    for (index in fields.indices) {
        if (fields[index].isBlank()) return failure(RejectionReason.EmptyField(fieldNames[index]))
    }

    val dateText = fields[0]
//    if (!strictDate.matches(dateText)) return failure(RejectionReason.InvalidDate(InvalidDateType.FORMAT, dateText))
    if (!strictDate.matches(dateText)) return failure(RejectionReason.InvalidDate(dateText))
    val date = try {
        LocalDate.parse(dateText)
    } catch (_: DateTimeParseException) {
//        return failure(RejectionReason.InvalidDate(InvalidDateType.DATE, dateText))
        return failure(RejectionReason.InvalidDate(dateText))
    }
//    actually we should check only 0 year cause in LocalDate year is already between 0 and 9999
//    if (date.year !in 1..9999) return failure(RejectionReason.InvalidDate(InvalidDateType.YEAR, date.year.toString()))
    if (date.year !in 1..9999) return failure(RejectionReason.InvalidDate(dateText))

//    checks session type
    val type = SessionType.entries.firstOrNull { it.name == fields[1] }
        ?: return failure(RejectionReason.UnknownType(fields[1]))

//    checks teachers amount for exam (>=2)
    val teachers = fields[4].split(',')
    if (type == SessionType.EXAM) {
        val actual = teachers.count { it.isNotBlank() && it != "-" }
        if (actual < 2) return failure(RejectionReason.ExamTeacherCount(actual))
    }

//    checks controls, then surrounding whitespace, then semantic errors.
    for (index in fields.indices) {
        if (fields[index].any { Character.isISOControl(it) }) {
            return failure(RejectionReason.ControlCharacter(fieldNames[index]))
        }
    }

//    checks topic and location for having surrounding spaces and then for teachers too
    for (index in 2..3) {
        val value = fields[index]
        if (value.first().isWhitespace() || value.last().isWhitespace()) {
            return failure(RejectionReason.SurroundingWhitespace(fieldNames[index]))
        }
    }
    for (name in teachers) {
        if (name.isNotEmpty() && (name.first().isWhitespace() || name.last().isWhitespace())) {
            return failure(RejectionReason.SurroundingWhitespace("teachers"))
        }
    }

//    canceled sessions shouldn't have "-" instead of location and teachers
    if (type == SessionType.CANCELLED) {
        if (fields[3] != "-") {
            return failure(RejectionReason.InvalidCancellationField("location", fields[3]))
        }
        if (fields[4] != "-") {
            return failure(RejectionReason.InvalidCancellationField("teachers", fields[4]))
        }
        return ParseResult.Success(Cancelled(date, fields[2]))
    }

//    location shouldn't be "-"
    if (fields[3] == "-") return failure(RejectionReason.InvalidLocation(fields[3]))

//    teaches shouldn't be blank and "-"
    for ((index, name) in teachers.withIndex()) {
        if (name.isBlank() || name == "-") {
            return failure(RejectionReason.InvalidTeacher(index + 1, name))
        }
    }
//    check for duplicate teachers
    val seen = mutableSetOf<String>()
    for (name in teachers) {
        if (!seen.add(name)) return failure(RejectionReason.DuplicateTeacher(name))
    }

//    makes session by their type
    val session = when (type) {
        SessionType.LECTURE -> Lecture(date, fields[2], fields[3], teachers)
        SessionType.PRACTICE -> Practice(date, fields[2], fields[3], teachers)
        SessionType.EXAM -> Exam(date, fields[2], fields[3], teachers)
        SessionType.CANCELLED -> error("Already handled cancellation")
    }
    return ParseResult.Success(session)
}
