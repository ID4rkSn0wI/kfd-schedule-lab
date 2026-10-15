package schedule

import java.time.LocalDate

/**
    Closed, immutable domain hierarchy. Every constructor enforces its contract.
    By using java.util.List.listOf we ensure that no one out of class can change lists.
 */
sealed interface Session {
    val date: LocalDate
    val topic: String
}

private fun validateCommon(date: LocalDate, topic: String) {
    //Unnecessary requirement: LocalDate year is always between 0001 and 9999
    require(date.year in 1..9999) { "Session year must be between 1 and 9999" }
    validateText(topic, "Topic")
}

private fun validateText(value: String, field: String) {
    require(value.isNotBlank()) { "$field must not be blank" }
    require(value.none { Character.isISOControl(it) }) {
        "$field must not contain control characters"
    }
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "$field must not have surrounding whitespace"
    }
/**
 * Another possible implementation
 */
//    require(value.trim() == value) {
//        "$field must not have surrounding whitespace"
//    }
}

private fun validatePlace(place: String) {
    validateText(place, "Location")
    require(place != "-") { "Location must not be '-'" }
}

private fun validateTeachers(teachers: List<String>, minimum: Int) {
    require(teachers.size >= minimum) {
        "At least $minimum teachers are required"
    }
    val seen = mutableSetOf<String>()
    for (name in teachers) {
        validateText(name, "Teacher")
        require(name != "-") { "Teacher must not be '-'" }
        require(seen.add(name)) { "Duplicate teacher: $name" }
    }
}

// not a data class cause of making a copy of teachers to ensure its immutability
class Lecture(
    override val date: LocalDate,
    override val topic: String,
    val place: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = java.util.List.copyOf(teachers)

    init {
        validateCommon(date, topic)
        validatePlace(place)
        validateTeachers(this.teachers, 1)
    }
}

// not a data class cause of making a copy of teachers to ensure its immutability
class Practice(
    override val date: LocalDate,
    override val topic: String,
    val place: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = java.util.List.copyOf(teachers)

    init {
        validateCommon(date, topic)
        validatePlace(place)
        validateTeachers(this.teachers, 1)
    }
}


// not a data class cause of making a copy of teachers to ensure its immutability
class Exam(
    override val date: LocalDate,
    override val topic: String,
    val place: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = java.util.List.copyOf(teachers)

    init {
        validateCommon(date, topic)
        validatePlace(place)
        validateTeachers(this.teachers, 2)
    }
}

// not a data class for privacy reasons (if it was data class, it could leak data cause of auto-generated copy() method)
class Cancelled(
    override val date: LocalDate,
    override val topic: String,
) : Session {
    init {
        validateCommon(date, topic)
    }
}
