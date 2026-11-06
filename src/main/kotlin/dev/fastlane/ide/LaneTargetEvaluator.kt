package dev.fastlane.ide

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/** Supplies a named target for standard Find Usages, both on declarations and calls. */
class LaneTargetEvaluator : TargetElementEvaluatorEx2() {
    override fun adjustReferenceOrReferencedElement(file: PsiFile, editor: Editor, offset: Int, flags: Int, referencedElement: PsiElement?): PsiElement? =
        targetAt(file, offset) ?: referencedElement

    private fun targetAt(file: PsiFile, offset: Int): PsiElement? {
        if (!FastlaneFiles.isFastfile(file.name)) return null
        val declaration = LaneCalls.declarationAt(offset, FastlaneAnalysis.declarations(file))
            ?: FastlaneAnalysis.calls(file).firstOrNull { it.range.containsOffset(offset) }?.targets?.singleOrNull()
            ?: return null
        return LaneNavigationElement(file, declaration)
    }
}
