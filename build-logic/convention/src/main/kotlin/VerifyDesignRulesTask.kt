import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when feature code bypasses the Bedrock design system:
 * Material tokens instead of `BedrockTheme`, ripple clicks instead of `Modifier.pressable`,
 * hard-coded colours instead of tokens, glows, or springs that overshoot.
 */
abstract class VerifyDesignRulesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    init {
        group = "verification"
        description = "Checks feature sources against the Bedrock design rules."
    }

    @TaskAction
    fun verify() {
        val violations = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                RULES.firstOrNull { it.pattern.containsMatchIn(code) }
                    ?.let { "${file.path}:${index + 1}: ${it.message}" }
            }
        }
        report.get().asFile.writeText(violations.joinToString("\n"))
        if (violations.isNotEmpty()) {
            throw GradleException("Bedrock design rule violations:\n" + violations.joinToString("\n"))
        }
    }

    private class Rule(val pattern: Regex, val message: String)

    private companion object {
        val RULES = listOf(
            Rule(Regex("""\bMaterialTheme\."""), "use BedrockTheme.colors / BedrockTheme.typography, not MaterialTheme"),
            Rule(Regex("""\.clickable\s*[({]"""), "use Modifier.pressable {} (no ripples)"),
            Rule(Regex("""\.combinedClickable\s*[({]"""), "use Modifier.pressable(onLongClick = …) {}"),
            Rule(Regex("""\bripple\s*\("""), "ripples are not part of Bedrock; use Modifier.pressable"),
            Rule(Regex("""\bColor\s*\(\s*0x"""), "hard-coded colour; use a BedrockTheme.colors token"),
            Rule(Regex("""\bdev\.redfox\.obsidian\b"""), "Obsidian is gone; use dev.redfox.bedrock"),
            Rule(Regex("""\.(glow|breathingGlow|ambientFloat)\s*\("""), "no glows in Bedrock: colour lives inside surfaces"),
            Rule(Regex("""dampingRatio\s*=\s*0\."""), "nothing overshoots: use BrMotion.quick/settle/glide"),
        )
    }
}
