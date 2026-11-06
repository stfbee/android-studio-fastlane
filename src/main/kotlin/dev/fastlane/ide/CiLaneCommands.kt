package dev.fastlane.ide

/** A deliberately limited, non-executing reader for literal CI shell commands. */
object CiLaneCommands {
    enum class Kind { GITHUB, GITLAB }
    data class Call(val lane: String, val platform: String?, val start: Int, val end: Int, val directory: String?)
    private data class Node(
        val key: String?, val value: String, val start: Int, val indent: Int,
        val parent: Node?, val children: MutableList<Node> = mutableListOf(), var block: String? = null,
    )
    private val identifier = Regex("[A-Za-z_][A-Za-z_0-9]*[!?]?")
    private val mapping = Regex("([A-Za-z_][A-Za-z_0-9.-]*):(?:[ \\t]+(.*))?")

    fun find(text: String, kind: Kind): List<Call> {
        val root = yaml(text)
        val result = mutableListOf<Call>()
        fun visit(node: Node) {
            if ((kind == Kind.GITHUB && node.key == "run" && node.parent?.key == null &&
                    node.parent?.parent?.key == "steps") ||
                (kind == Kind.GITLAB && node.key in setOf("script", "before_script", "after_script") &&
                    (node.parent === root || (node.parent?.parent === root &&
                        node.parent.key !in setOf("variables", "workflow", "include"))))) {
                val directory = if (kind == Kind.GITHUB) workingDirectory(node) else "."
                if (kind == Kind.GITLAB && node.parent?.children?.any { it.key == "extends" } == true) return
                val state = ShellState(directory)
                if (kind == Kind.GITLAB && node.key == "script") {
                    val before = node.parent?.children?.firstOrNull { it.key == "before_script" }
                        ?: root.children.firstOrNull { it.key == "default" }?.children?.firstOrNull { it.key == "before_script" }
                        ?: root.children.firstOrNull { it.key == "before_script" }
                    if (before != null) {
                        val setup = scripts(before)
                        if (setup.isEmpty() && before.value != "[]") state.directory = null
                        setup.forEach { (source, start) -> shell(source, start, state, mutableListOf()) }
                    }
                }
                scripts(node).forEach { (source, start) -> shell(source, start, state, result) }
                return
            }
            node.children.forEach(::visit)
        }
        visit(root)
        return result
    }

    /** Supports block mappings, sequences and literal | blocks; aliases/flow YAML are left alone. */
    private fun yaml(text: String): Node {
        val root = Node("", "", 0, -1, null)
        val stack = mutableListOf(root)
        var offset = 0
        val lines = text.splitToSequence('\n').toList()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val indent = line.takeWhile { it == ' ' }.length
            val body = line.substring(indent).trimEnd('\r')
            if (body.isBlank() || body.startsWith('#')) { offset += line.length + 1; index++; continue }
            while (stack.last().indent >= indent) stack.removeLast()
            var parent = stack.last()
            var content = body
            var start = offset + indent
            var keyIndent = indent
            if (body.startsWith("- ") || body == "-") {
                val item = Node(null, "", start, indent, parent)
                parent.children += item
                stack += item
                parent = item
                content = body.drop(1).trimStart()
                start = offset + line.indexOf(content, indent + 1).coerceAtLeast(indent + 1)
                keyIndent = indent + 2
            }
            val match = mapping.matchEntire(content)
            val value = match?.groupValues?.get(2) ?: content
            val valueStart = start + (match?.groups?.get(2)?.range?.first ?: if (match != null) content.length else 0)
            val node = Node(match?.groupValues?.get(1), value, valueStart, keyIndent, parent)
            parent.children += node
            stack += node
            offset += line.length + 1
            index++
            if (Regex("\\|[+-]?(?:\\s+#.*)?").matches(value)) {
                val begin = offset
                while (index < lines.size && (lines[index].isBlank() ||
                        lines[index].takeWhile { it == ' ' }.length > keyIndent)) {
                    offset += lines[index].length + 1
                    index++
                }
                node.block = text.substring(begin.coerceAtMost(text.length), offset.coerceAtMost(text.length))
                // The shell sees indentation as whitespace; retain it to preserve editor offsets.
                node.children += Node(null, node.block!!, begin, keyIndent + 1, node)
            }
        }
        return root
    }

    private fun scalar(node: Node): Pair<String, Int>? {
        val value = node.value.trimEnd()
        if (value.isEmpty() || value.first() in "*&[{>!" || value.startsWith('|')) return null
        if (value.first() in "\"'") {
            val quote = value.first()
            val end = value.lastIndexOf(quote)
            if (end <= 0 || value.substring(end + 1).trim().let { it.isNotEmpty() && !it.startsWith('#') }) return null
            val inner = value.substring(1, end)
            // Decoding escapes would change source offsets. Leave these uncommon forms unsupported.
            if ((quote == '"' && '\\' in inner) || (quote == '\'' && "''" in inner)) return null
            return inner to node.start + 1
        }
        return value to node.start
    }

    private fun scripts(node: Node): List<Pair<String, Int>> = when {
        node.block != null -> listOf(node.children.last().let { it.value to it.start })
        node.value.isNotBlank() -> listOfNotNull(scalar(node))
        else -> node.children.flatMap(::scripts)
    }

    private fun workingDirectory(run: Node): String? {
        var setting = run.parent!!.children.firstOrNull { it.key == "working-directory" }
        // Step > job defaults > workflow defaults.
        var scope: Node? = run.parent.parent?.parent
        while (setting == null && scope != null) {
            setting = scope.children.firstOrNull { it.key == "defaults" }?.children
                ?.firstOrNull { it.key == "run" }?.children?.firstOrNull { it.key == "working-directory" }
            scope = scope.parent
        }
        if (setting == null) return "."
        val value = scalar(setting)?.first ?: return null
        return value.substringBefore(" #").takeUnless { it.any { ch -> ch in "\$`{}" } }
    }

    private data class Token(val value: String, val start: Int, val end: Int, val literal: Boolean)
    private class ShellState(var directory: String?)

    private fun shell(source: String, base: Int, state: ShellState, result: MutableList<Call>) {
        // Heredoc bodies and shell programs require a full shell parser; don't mistake their text for commands.
        if ("<<" in source || Regex("(?m)^\\s*(?:if|for|while|until|case|function)\\b|\\$\\(|\\(\\s*cd\\b").containsMatchIn(source)) return
        val command = mutableListOf<Token>()
        fun flush() {
            val args = command.dropWhile { Regex("[A-Za-z_][A-Za-z_0-9]*=.*").matches(it.value) }
            if (args.firstOrNull()?.value in setOf("source", ".", "eval", "pushd", "popd")) state.directory = null
            if (args.firstOrNull()?.value == "cd") {
                val path = args.getOrNull(1)?.takeIf { it.literal && it.value != "-" && !it.value.startsWith('~') && !it.value.startsWith('/') }?.value
                state.directory = if (state.directory != null && path != null) "${state.directory}/$path" else null
            }
            val prefix = when {
                args.firstOrNull()?.value == "fastlane" && args[0].literal -> 1
                args.take(3).map { it.value } == listOf("bundle", "exec", "fastlane") && args.take(3).all { it.literal } -> 3
                else -> 0
            }
            if (prefix > 0) {
                val first = args.getOrNull(prefix)
                val second = args.getOrNull(prefix + 1)
                if (first != null && first.literal && identifier.matches(first.value)) {
                    val lane = second?.takeIf { it.literal && identifier.matches(it.value) } ?: first
                    // A dynamic second argument could be the lane name; never fall back to its platform.
                    if (second == null || second.literal) result += Call(lane.value,
                        first.value.takeIf { lane !== first }, base + lane.start, base + lane.end, state.directory)
                }
            }
            command.clear()
        }
        var index = 0
        while (index < source.length) {
            val c = source[index]
            if (c == '\\' && source.getOrNull(index + 1) == '\n') { index += 2; continue }
            if (c in "\n;|&") { flush(); index++; continue }
            if (c.isWhitespace()) { index++; continue }
            if (c == '#') { while (index < source.length && source[index] != '\n') index++; continue }
            val start = index
            val word = StringBuilder()
            var quote: Char? = null
            var literal = true
            var nameStart = start
            var nameEnd = start
            while (index < source.length) {
                val ch = source[index]
                if (quote == null && (ch.isWhitespace() || ch in ";|&")) break
                if (ch in "\"'" && (quote == null || quote == ch)) {
                    if (quote == null) { quote = ch; if (word.isEmpty()) nameStart = index + 1 }
                    else { quote = null; nameEnd = index }
                    index++; continue
                }
                if (ch in "\$`" && quote != '\'') literal = false
                if (ch in "\\<>()") literal = false
                word.append(ch)
                index++
                nameEnd = index
            }
            if (quote != null) literal = false
            command += Token(word.toString(), nameStart, nameEnd, literal)
        }
        flush()
    }
}
