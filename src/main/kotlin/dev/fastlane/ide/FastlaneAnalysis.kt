package dev.fastlane.ide

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer

object FastlaneAnalysis {
    private data class Analysis(val declarations: List<Declaration>, val calls: List<LaneCalls.Call>)

    private fun analysis(file: PsiFile): Analysis = CachedValuesManager.getCachedValue(file) {
        val text = file.text
        val code = codeOnly(text)
        val declarations = Outline.parse(text, code)
        CachedValueProvider.Result.create(Analysis(declarations, LaneCalls.find(code, declarations)), file)
    }
    fun declarations(file: PsiFile): List<Declaration> = analysis(file).declarations
    fun calls(file: PsiFile): List<LaneCalls.Call> = analysis(file).calls

    fun analyze(text: String): List<Declaration> = Outline.parse(text, codeOnly(text))

    /** Reuse the IDE's Ruby grammar: it already understands heredocs, % literals and regexes. */
    fun codeOnly(text: String): String {
        val service = TextMateService.getInstance()
        service.ensureInitialized()
        val ruby = service.getLanguageDescriptorByFileName("sample.rb") ?: return " ".repeat(text.length)
        val lexer = TextMateHighlightingLexer(ruby, -1)
        val code = text.toCharArray()
        lexer.start(text)
        while (lexer.tokenType != null) {
            ProgressManager.checkCanceled()
            var scope = (lexer.tokenType as? TextMateElementType)?.scope
            var ignored = false
            while (scope != null) {
                val name = scope.scopeName.toString()
                if (name.startsWith("string.") || name.startsWith("comment.")) ignored = true
                scope = scope.parent
            }
            if (ignored) {
                for (i in lexer.tokenStart until lexer.tokenEnd) if (code[i] != '\n') code[i] = ' '
            }
            lexer.advance()
        }
        return String(code)
    }
}
