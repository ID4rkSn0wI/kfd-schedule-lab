package schedule

import java.time.LocalDate

/** Made an optional task for transferring a session to a valid date. */
sealed interface TransferResult {
    data class Moved(val session: Session) : TransferResult
    data object CancelledSession : TransferResult
}

/** Does not modify the source and saves original session type and teacher order. */
fun transferSession(session: Session, newDate: LocalDate): TransferResult {
//    actually we should check only 0 year cause in LocalDate year is already between 0 and 9999
    require(newDate.year in 1..9999) { "Session year must be between 1 and 9999" }
    return when (session) {
        is Lecture -> TransferResult.Moved(Lecture(newDate, session.topic, session.place, session.teachers))
        is Practice -> TransferResult.Moved(Practice(newDate, session.topic, session.place, session.teachers))
        is Exam -> TransferResult.Moved(Exam(newDate, session.topic, session.place, session.teachers))
        is Cancelled -> TransferResult.CancelledSession
    }
}
