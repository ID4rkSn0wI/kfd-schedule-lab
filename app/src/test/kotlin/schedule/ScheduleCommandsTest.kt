package schedule

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals

class ScheduleCommandsTest {
    @Test
    fun filtersInclusiveBoundariesAndChecksRejectedRowsOutsideRange(@TempDir directory: Path) {
        val path = directory.resolve("data.txt")
        Files.writeString(
            path,
            listOf(
                "2026-10-04|LECTURE|Before|Room 1|Ivanov",
                "2026-10-05|LECTURE|From|Room 2|Ivanov",
                "2026-10-06|EXAM|Middle|Room 3|Ivanov,Petrov",
                "2026-10-07|PRACTICE|To|Room 4|Petrov",
                "2026-10-08|CANCELLED|After|-|-",
                "2026-13-45|LECTURE|Bad outside|Room 1|Ivanov",
            ).joinToString("\r\n", postfix = "\r\n"),
        )
        val from = LocalDate.of(2026, 10, 5)
        val to = LocalDate.of(2026, 10, 7)
        val (code, output) = captured { runPeriod(Command.Period(path.toString(), from, to)) }
        assertEquals(1, code)
        assertEquals(
            listOf(
                "2026-10-05 | LECTURE | From | Room 2",
                "2026-10-06 | EXAM | Middle | Room 3",
                "2026-10-07 | PRACTICE | To | Room 4",
                "Total: 3",
            ).joinToString(separator = "\n", postfix = "\n"),
            output,
        )
        val (typeCode, typeOutput) = captured {
            runType(Command.ByType(path.toString(), SessionType.EXAM))
        }
        assertEquals(1, typeCode)
        assertEquals("2026-10-06 | EXAM | Middle | Room 3 | Ivanov, Petrov\nTotal: 1\n", typeOutput)
        val (errorCode, errorOutput) = captured { runErrors(Command.Errors(path.toString())) }
        assertEquals(1, errorCode)
        assertEquals("line 6: invalid date \"2026-13-45\"\nRejected: 1 of 6\n", errorOutput)
    }

    @Test
    fun printsZeroSummariesForEmptyFile(@TempDir directory: Path) {
        val file = directory.resolve("empty.txt")
        Files.writeString(file, "")
        val day = LocalDate.of(2026, 10, 5)
        val (periodCode, period) = captured { runPeriod(Command.Period(file.toString(), day, day)) }
        assertEquals(0, periodCode)
        assertEquals("Total: 0\n", period)
        val (typeCode, type) = captured { runType(Command.ByType(file.toString(), SessionType.CANCELLED)) }
        assertEquals(0, typeCode)
        assertEquals("Total: 0\n", type)
        val (errorsCode, errors) = captured { runErrors(Command.Errors(file.toString())) }
        assertEquals(0, errorsCode)
        assertEquals("Rejected: 0 of 0\n", errors)
    }

    @Test
    fun keepsFileOrderForRepeatedValidRows(@TempDir directory: Path) {
        val file = directory.resolve("duplicate.txt")
        val row = "2026-10-05|CANCELLED|Postponed|-|-"
        Files.writeString(file, "$row\n$row\n")
        val (code, output) = captured {
            runType(Command.ByType(file.toString(), SessionType.CANCELLED))
        }
        assertEquals(0, code)
        assertEquals(
            listOf(
                "2026-10-05 | CANCELLED | Postponed | - | -",
                "2026-10-05 | CANCELLED | Postponed | - | -",
                "Total: 2",
            ).joinToString(separator = "\n", postfix = "\n"),
            output,
        )
    }

    private fun captured(command: () -> Int): Pair<Int, String> {
        val previous = System.out
        val bytes = ByteArrayOutputStream()
        return try {
            System.setOut(PrintStream(bytes, true, StandardCharsets.UTF_8))
            val code = command()
            code to bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n")
        } finally {
            System.setOut(previous)
        }
    }
}
