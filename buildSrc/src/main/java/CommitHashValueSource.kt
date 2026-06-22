import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject

abstract class CommitHashValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        System.getenv("GITSHA")?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        val ran = runCatching {
            execOperations.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                standardOutput = output
                errorOutput = error
                isIgnoreExitValue = true
            }
        }.isSuccess
        if (!ran) return FALLBACK
        return String(output.toByteArray(), Charset.defaultCharset()).trim().ifEmpty { FALLBACK }
    }

    private companion object {
        const val FALLBACK = "unknown"
    }
}
