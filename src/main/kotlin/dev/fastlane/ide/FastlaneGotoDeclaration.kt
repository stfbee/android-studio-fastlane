package dev.fastlane.ide

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class FastlaneGotoDeclaration : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val file = sourceElement?.containingFile ?: return null
        if (!FastlaneFiles.isFastfile(file.name)) return null
        val declaration = LaneCalls.declarationAt(offset, FastlaneAnalysis.declarations(file))
        if (declaration != null) {
            return FastlaneAnalysis.calls(file)
                .filter { call -> call.targets.any { it.nameOffset == declaration.nameOffset } }
                .map { LaneCallNavigationElement(file, declaration.name, it.range) }.toTypedArray()
        }
        val targets = FastlaneAnalysis.calls(file).firstOrNull { it.range.containsOffset(offset) }?.targets ?: return null
        return targets.map { LaneNavigationElement(file, it) }.toTypedArray()
    }
}
