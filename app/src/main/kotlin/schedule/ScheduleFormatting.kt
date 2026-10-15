package schedule

/** Raw values are stayed the same, ASCII-only diagnostic messages.
 * Made so we have a proper output for not-ASCII symbols and Russian language */
fun escapeDiagnostic(value: String): String = buildString {
    for (char in value) {
        when {
            char == '\\' -> append("\\\\")
            char == '"' -> append("\\\"")
            char.code !in 0x20..0x7E -> {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0').uppercase())
            }
            else -> append(char)
        }
    }
}

// does what it says: formats failures by its type
fun formatFailure(failure: ParseResult.Failure): String {
    val message = when (val reason = failure.reason) {
        RejectionReason.InvalidSeparator -> "invalid field separator"
        is RejectionReason.WrongFieldCount ->
            "got ${reason.actual} fields, expected 5"
        is RejectionReason.EmptyField ->
            "empty required field \"${reason.field}\""
//        is RejectionReason.InvalidDate -> {when (val type = reason.type) {
//            InvalidDateType.DATE -> "invalid date \"${escapeDiagnostic(reason.value)}\""
//            InvalidDateType.FORMAT -> "invalid date format \"${escapeDiagnostic(reason.value)}\" should be XXXX-XX-XX"
//            InvalidDateType.YEAR -> "invalid date year \"${escapeDiagnostic(reason.value)}\" should be between 1 and 9999"
//        }}
        is RejectionReason.InvalidDate -> "invalid date \"${escapeDiagnostic(reason.value)}\""
        is RejectionReason.UnknownType ->
            "unknown session type \"${escapeDiagnostic(reason.value)}\""
        is RejectionReason.ExamTeacherCount ->
            "type \"EXAM\" requires at least 2 teachers, got ${reason.actual}"
        is RejectionReason.ControlCharacter ->
            "control character in field \"${reason.field}\""
        is RejectionReason.SurroundingWhitespace ->
            "surrounding whitespace in field \"${reason.field}\""
        is RejectionReason.InvalidCancellationField ->
            "type \"CANCELLED\" requires \"-\" in field \"${reason.field}\", got \"${escapeDiagnostic(reason.value)}\""
        is RejectionReason.InvalidLocation ->
            "invalid location \"${escapeDiagnostic(reason.value)}\""
        is RejectionReason.InvalidTeacher ->
            "invalid teacher at position ${reason.position} \"${escapeDiagnostic(reason.value)}\""
        is RejectionReason.DuplicateTeacher ->
            "duplicate teacher \"${escapeDiagnostic(reason.value)}\""
    }
    return "line ${failure.lineNumber}: $message"
}

// a fine utility function for getting a session type by session class
fun sessionType(session: Session): SessionType = when (session) {
    is Lecture -> SessionType.LECTURE
    is Practice -> SessionType.PRACTICE
    is Exam -> SessionType.EXAM
    is Cancelled -> SessionType.CANCELLED
}

// formats session to a proper output
fun formatSession(session: Session, includeTeachers: Boolean): String {
    val location = when (session) {
        is Lecture -> session.place
        is Practice -> session.place
        is Exam -> session.place
        is Cancelled -> "-"
    }
    val prefix = "${session.date} | ${sessionType(session).name} | ${session.topic} | $location"
    if (!includeTeachers) return prefix
    val names = when (session) {
        is Lecture -> session.teachers.joinToString(", ")
        is Practice -> session.teachers.joinToString(", ")
        is Exam -> session.teachers.joinToString(", ")
        is Cancelled -> "-"
    }
    return "$prefix | $names"
}
