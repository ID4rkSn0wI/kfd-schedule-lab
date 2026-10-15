package schedule

interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}

/** Keeps insertion order and duplicates, others can't mutate the repository. */
class InMemorySessionRepository : SessionRepository {
    private val sessions = mutableListOf<Session>()

    override fun add(session: Session) {
        sessions.add(session)
    }

    override fun addAll(sessions: List<Session>) {
        this.sessions.addAll(sessions)
    }

//    returns copy so private field can't be changes
    override fun all(): List<Session> = java.util.List.copyOf(sessions)
}

// just a small data class for keeping a data for an event
data class AuditEvent(val operation: String, val count: Int)

/** Delegation forwards reads and storage. Additions are explicitly intercepted once. */
class AuditedSessionRepository(
    private val delegate: SessionRepository,
) : SessionRepository by delegate {
    private val events = mutableListOf<AuditEvent>()

//    made it private set so it can be read outside, but changed only inside
    var addedCount: Int = 0
        private set

//    returns copy so private field can't be changes
    val auditEvents: List<AuditEvent>
        get() = java.util.List.copyOf(events)

    override fun add(session: Session) {
        delegate.add(session)
        addedCount += 1
        events.add(AuditEvent("add", 1))
    }

    override fun addAll(sessions: List<Session>) {
        delegate.addAll(sessions)
        addedCount += sessions.size
        events.add(AuditEvent("addAll", sessions.size))
    }
}
