package dev.fastlane.ide

import com.intellij.openapi.util.TextRange

/** One resolution rule shared by forward navigation and Find Usages. */
object LaneCalls {
    private val identifiers = Regex("[A-Za-z_][A-Za-z_0-9]*[!?]?")
    data class Call(val range: TextRange, val targets: List<Declaration>)

    fun find(code: String, declarations: List<Declaration>): List<Call> {
        val all = Outline.flatten(declarations)
        val names = all.filter { it.kind != "platform" }.map { it.name }.toSet()
        return identifiers.findAll(code).mapNotNull { token ->
            if (token.value !in names) return@mapNotNull null
            val start = token.range.first
            val end = token.range.last + 1
            if (all.any { start == it.offset || start == it.nameOffset }) return@mapNotNull null
            // Ruby receiver calls, symbols, variables, labels and method definitions aren't lane calls.
            val before = code.substring(0, start).trimEnd(' ', '\t', '\r')
            if (before.lastOrNull() in listOf('.', ':', '@', '$') ||
                Regex("\\b(?:def|class|module|alias|undef)\\s+$").containsMatchIn(before)) return@mapNotNull null
            val after = code.substring(end).trimStart(' ', '\t', '\r')
            if (after.startsWith(":") || (after.startsWith("=") && !after.startsWith("==") && !after.startsWith("=>"))) return@mapNotNull null
            val platform = declarations.lastOrNull { it.kind == "platform" && start in it.offset until it.endOffset }
            val local = platform?.children.orEmpty().filter { it.kind != "platform" && it.name == token.value }
            val targets = local.ifEmpty { declarations.filter { it.kind != "platform" && it.name == token.value } }
            targets.takeIf { it.isNotEmpty() }?.let { Call(TextRange(start, end), it) }
        }.toList()
    }

    fun declarationAt(offset: Int, declarations: List<Declaration>): Declaration? =
        Outline.flatten(declarations).firstOrNull {
            it.kind != "platform" && offset in it.nameOffset..it.nameOffset + it.name.length
        }
}
