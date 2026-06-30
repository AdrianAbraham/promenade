package net.shadowspire.promenade2.data.diagnostics

data class LibraryDiagnostic(
    val severity: Severity,
    val fileName: String?,
    val message: String,
)

enum class Severity {
    Warning,
    Error,
}
