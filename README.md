# Schedule Lab — "Nothing Crashes"

## Project structure

Application code is in app/src/main/kotlin/schedule; tests are in app/src/test/kotlin/schedule.

| File | Purpose |
|---|---|
| **Session.kt** | Sealed Session interface and four concrete classes: Lecture, Practice, Exam, and Cancelled |
| **SessionRepository.kt** | SessionRepository, InMemorySessionRepository, AuditedSessionRepository, and AuditEvent |
| **ScheduleParser.kt** | parseScheduleLine, the sealed ParseResult hierarchy, and structured RejectionReason values |
| **ScheduleFormatting.kt** | Formatting sessions and errors, including diagnostic escaping |
| **Commands.kt** | period, type, and errors command handlers |
| **SessionTransfer.kt** | Optional typed session rescheduling |
| **Cli.kt** | Provided CLI argument parser, unchanged |
| **Main.kt** | Provided dispatch and error handling, unchanged |
| **FileLines.kt** | Provided line-by-line file reading, unchanged |

## Design decisions and invariants

### Why regular classes instead of data classes?

Lecture, Practice, Exam and Cancelled are ordinary Kotlin classes implementing the sealed Session interface. Ordinary classes do not automatically generate copy() or componentN() methods. Each constructor validates all applicable invariants, and the publicly exposed properties are read-only. There is no generated copy() method that needs a separate invariant-safety guarantee.

Cancelled declares **only the date and topic**. It has no location or teacher properties, including nullable properties.

### Constructor validation

Every session enforces the following rules:

- The year is between 1 and 9999.
- The topic is neither empty nor entirely whitespace.
- Text fields contain no control characters or surrounding whitespace.
- Internal spaces are permitted.
- Active sessions have a nonempty location other than a single hyphen (-).
- Lectures and practices require at least one teacher.
- Exams require at least two distinct teachers.
- Teacher names must not be blank, equal to a single hyphen, or duplicated.
- Teacher names are case-sensitive: Ivanov and ivanov are distinct.
- Teacher order is preserved.

The model does not trim spaces, change case, or remove duplicates. Invalid direct constructor calls produce IllegalArgumentException. Validation is not limited to file parsing.

### Immutable collections

Each active session saves java.util.List.copyOf(teachers) and validates that stored copy. This protects against two distinct modifications:

1. Changing the original MutableList after construction does not affect the session.
2. Trying to modify the list returned by the session's teachers property causes UnsupportedOperationException.

Similarly, InMemorySessionRepository.all() returns an independent, unmodifiable List.copyOf snapshot. Later additions cannot change snapshots obtained earlier. The repository preserves insertion order and allows duplicate sessions.

### Why override both add and addAll?

AuditedSessionRepository implements its interface using SessionRepository by delegate. Interface delegation forwards each method directly to the delegate object. Overriding add alone does **not** intercept addAll calls, even if the delegate's addAll method internally calls its own add method.

For this reason, the wrapper explicitly overrides both operations. After each successful delegated call, it:

- Records a single AuditEvent with the operation name and number of added sessions.
- Increases addedCount by the number of added sessions.
- Records addAll(emptyList()) as one event with count 0.
- Does not create events for all().
- Does not record successful additions if a delegated operation throws.

The audit log is exposed through an unmodifiable snapshot. Audit events are not printed as part of CLI output.

## Input format and parsing

Each file line contains exactly five fields:

    date|type|topic|location|teachers

For example:

    2026-10-05|LECTURE|Classes and delegation|Room 312|Portnov
    2026-10-08|PRACTICE|Domain modeling|Room 312|Anokhin
    2026-10-15|EXAM|Oral exam 1|Room 401|Portnov,Stepanova
    2026-10-19|CANCELLED|Lecture postponed|-|-

The delimiter is a pipe (|), and trailing empty fields must still count. Teachers are comma-separated. Dates must use YYYY-MM-DD, with a four-digit year between 0001 and 9999, and must be valid calendar dates. Types are case-sensitive: LECTURE, PRACTICE, EXAM, and CANCELLED. Both the location and teacher fields for CANCELLED must be exactly a single hyphen (-). Empty lines are invalid; an empty file contains no lines. A trailing newline does not create another record.

The parser returns either ParseResult.Success(session) or ParseResult.Failure(lineNumber, reason). Failures contain a structured reason rather than a preformatted message or an exception. A separate formatting function converts the reason into an English diagnostic.

### Error priority

The parser returns only one error per line. Validation follows this priority:

1. No pipe delimiter: invalid field separator.
2. A number of fields other than five.
3. The first empty or whitespace-only required field, in order: date, type, topic, location, teachers.
4. Invalid date format or calendar value.
5. Unknown session type.
6. EXAM with fewer than two nonblank teacher entries other than the single hyphen.
7. Remaining domain errors, in this order:
   - Control characters.
   - Surrounding whitespace.
   - Invalid cancellation fields.
   - Invalid location for an active session.
   - Invalid teacher entries.
   - Duplicate teacher names.

When multiple fields fail the same stage, the earliest field or teacher entry takes priority. The required error messages for invalid separators, field counts, empty fields, invalid dates, unknown types, and insufficient exam teachers use the exact wording from the assignment.

### Additional error messages

These are the English diagnostic messages defined for the other structured reasons. Every message is prefixed by line N: followed by a space.

| Reason | Message |
|---|---|
| ControlCharacter(field) | control character in field "FIELD" |
| SurroundingWhitespace(field) | surrounding whitespace in field "FIELD" |
| InvalidCancellationField(field, value) | type "CANCELLED" requires "-" in field "FIELD", got "VALUE" |
| InvalidLocation(value) | invalid location "VALUE" |
| InvalidTeacher(position, value) | invalid teacher at position POSITION "VALUE" |
| DuplicateTeacher(value) | duplicate teacher "VALUE" |

Values in diagnostic messages are escaped so damaged input cannot add extra lines or non-ASCII text: a backslash becomes two backslashes, a double quote is prefixed with a backslash, and control or non-ASCII UTF-16 code units are represented as \uXXXX (four hexadecimal digits per code unit). **Only the error formatter escapes these values**; the domain model never modifies them automatically.

## Tests

The custom tests are in app/src/test/kotlin/schedule.

| Attempt | Protection |
|---|---|
| Construct a session with an empty or all-whitespace topic | Constructor validation through validateText |
| Use year 0 or 10000 | Constructor validation through validateCommon |
| Construct an active session without teachers | Minimum teacher-count check |
| Construct an exam with only one teacher | Exam constructor requires at least two |
| Construct an exam with duplicate teacher names | Constructor uniqueness check |
| Change the original MutableList after session construction | Defensive copy through List.copyOf |
| Modify a list exposed by a public property | Unmodifiable List.copyOf collection |
| Bypass validation using copy() | Regular session classes do not generate copy() |

Additional tests cover:

- Correct construction of all four session types.
- Case-sensitive teacher names and order preservation.
- Invalid whitespace, control characters, and location values.
- Repository snapshots, duplicates, and insertion order.
- Audit events for add, addAll, empty addAll, and failed delegate operations.
- Multiple errors on one line and validation priority.
- UTF-16 diagnostic escaping.
- LF and CRLF line endings, empty files, inclusive filtering, rejected records outside a filter, and output order.


## Optional task: rescheduling a session

The optional feature is implemented by transferSession(session, newDate), returning a typed TransferResult.

- TransferResult.Moved(newSession): successful transfer of an active session.
- TransferResult.CancelledSession: a typed refusal for a cancelled session.

A successful transfer creates a **new object of the same concrete session type** with the new date and the original topic, location, and teacher order. The original session remains unchanged. The new date is validated, and no extra CLI command is introduced. Tests are in SessionTransferTest.kt.
