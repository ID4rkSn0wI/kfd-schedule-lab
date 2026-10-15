package schedule

import java.io.IOException
import java.nio.file.InvalidPathException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runApplication(args))
}

// .\gradlew.bat :app:run --args="type --file samples/dirty.txt --type EXAM"
// .\gradlew.bat :app:run --args="errors --file samples/dirty.txt"
fun runApplication(
    args: Array<String>,
    handlers: CommandHandlers = StudentCommandHandlers,
    output: (String) -> Unit = ::println,
): Int {
    val command = try {
        parseCommand(args)
    } catch (_: InvalidArgumentsException) {
        output("Invalid arguments")
        return 2
    }
    return try {
        when (command) {
            is Command.Period -> handlers.period(command)
            is Command.ByType -> handlers.byType(command)
            is Command.Errors -> handlers.errors(command)
        }
    } catch (_: IOException) {
        output("Cannot read file")
        2
    } catch (_: InvalidPathException) {
        output("Cannot read file")
        2
    } catch (_: SecurityException) {
        output("Cannot read file")
        2
    }
}
