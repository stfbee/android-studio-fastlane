package dev.fastlane.ide

/** Static outline of literal Fastlane declarations, never evaluated Ruby. */
data class Declaration(
    val kind: String,
    val name: String,
    val offset: Int,
    val nameOffset: Int,
    var endOffset: Int,
    val description: String? = null,
    val children: MutableList<Declaration> = mutableListOf(),
)

object Outline {
    private val tokens = Regex("::|:[A-Za-z_][A-Za-z_0-9!?]*|[A-Za-z_][A-Za-z_0-9!?]*|[^\\s]|\\n")
    private val header = Regex("""\s*(?:\(\s*)?(?::([A-Za-z_][A-Za-z_0-9!?]*)|(['"])([A-Za-z_][A-Za-z_0-9!?]*)\2)\s*\)?\s*do\b""")
    private val description = Regex("""\s*\(?\s*(['"])([^\r\n]*?)\1\s*\)?\s*(?:#.*)?$""")
    private val kinds = setOf("platform", "lane", "private_lane", "override_lane")
    private data class Block(val kind: String, val declaration: Declaration? = null)

    /** [code] has strings, regexes and comments blanked out, with offsets/newlines preserved. */
    fun parse(text: String, code: String): List<Declaration> {
        require(text.length == code.length)
        val result = mutableListOf<Declaration>()
        val stack = mutableListOf<Block>()
        val pending = mutableMapOf<Int, Declaration>()
        val ts = tokens.findAll(code).toList()
        var desc: String? = null
        var loopAwaitingDo = false
        for ((index, token) in ts.withIndex()) {
            val word = token.value
            val previous = ts.getOrNull(index - 1)?.value
            val next = ts.getOrNull(index + 1)?.value
            if (word == "\n" || word == ";") {
                loopAwaitingDo = false
                continue
            }
            // Receiver calls and hash labels are not Ruby keywords or DSL declarations.
            if (previous == "." || previous == "::" || next == ":") continue
            val statementStart = previous == null || previous in setOf("\n", ";", "then", "else", "do")
            val inDefinition = stack.any { it.kind in setOf("def", "class", "module") || it.declaration?.kind in kinds - "platform" }
            if (word == "desc" && statementStart && !inDefinition) {
                val lineEnd = text.indexOf('\n', token.range.last + 1).let { if (it < 0) text.length else it }
                desc = description.matchEntire(text.substring(token.range.last + 1, lineEnd))
                    ?.groupValues?.get(2)?.takeUnless { "#{" in it }
            }
            if (word in kinds && statementStart && !inDefinition) {
                val tail = text.substring(token.range.last + 1, minOf(text.length, token.range.last + 1025))
                val match = header.find(tail)?.takeIf { it.range.first == 0 }
                if (match != null) {
                    val name = match.groups[1] ?: match.groups[3]!!
                    val start = token.range.last + 1
                    val doOffset = start + match.range.last - 1
                    // Ensure the opening do itself is code, not a comment/string.
                    if (code.regionMatches(doOffset, "do", 0, 2)) {
                        pending[doOffset] = Declaration(word, name.value, token.range.first,
                            start + name.range.first, text.length, desc)
                    }
                }
                desc = null
            } else if (statementStart && word != "desc") desc = null

            when (word) {
                "do" -> {
                    if (loopAwaitingDo) {
                        loopAwaitingDo = false
                    } else {
                        val declaration = pending.remove(token.range.first)
                        if (declaration != null) {
                            val parent = stack.lastOrNull { it.declaration?.kind == "platform" }?.declaration
                            (parent?.children ?: result).add(declaration)
                        }
                        stack.add(Block("do", declaration))
                    }
                }
                "end" -> {
                    stack.removeLastOrNull()?.declaration?.endOffset = token.range.last + 1
                }
                "class", "module", "case", "begin", "for", "def" -> {
                    // Endless methods have no closing end. Default argument '=' is inside parens.
                    val line = code.substring(token.range.last + 1).substringBefore('\n')
                    val endless = word == "def" && Regex("""^\s+[\w.?!]+(?:\([^\n]*\))?\s*=(?!=)""").containsMatchIn(line)
                    if (!endless) stack.add(Block(word))
                    if (word == "for") loopAwaitingDo = true
                }
                "if", "unless", "while", "until" -> {
                    val expressionStart = statementStart || previous in setOf("=", "(", "[", ",", "return")
                    if (expressionStart) {
                        stack.add(Block(word))
                        if (word == "while" || word == "until") loopAwaitingDo = true
                    }
                }
            }
        }
        return result
    }

    fun flatten(nodes: List<Declaration>): List<Declaration> = nodes.flatMap { listOf(it) + flatten(it.children) }
}
