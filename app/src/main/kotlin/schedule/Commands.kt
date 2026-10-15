package schedule

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

fun runPeriod(command: Command.Period): Int {
    var selected = 0L
    var rejected = false
    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseScheduleLine(number, line)) {
            is ParseResult.Success -> {
                if (result.session.date >= command.from && result.session.date <= command.to) {
                    println(formatSession(result.session, includeTeachers = false))
                    selected++
                }
            }
            is ParseResult.Failure -> rejected = true
        }
    }
    println("Total: $selected")
    return if (rejected) 1 else 0
}

fun runType(command: Command.ByType): Int {
    var selected = 0L
    var rejected = false
    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseScheduleLine(number, line)) {
            is ParseResult.Success -> {
                if (sessionType(result.session) == command.type) {
                    println(formatSession(result.session, includeTeachers = true))
                    selected++
                }
            }
            is ParseResult.Failure -> rejected = true
        }
    }
    println("Total: $selected")
    return if (rejected) 1 else 0
}

fun runErrors(command: Command.Errors): Int {
    var total = 0L
    var rejected = 0L
    forEachScheduleLine(command.file) { number, line ->
        total++
        when (val result = parseScheduleLine(number, line)) {
            is ParseResult.Success -> Unit
            is ParseResult.Failure -> {
                rejected++
                println(formatFailure(result))
            }
        }
    }
    println("Rejected: $rejected of $total")
    return if (rejected > 0) 1 else 0
}
