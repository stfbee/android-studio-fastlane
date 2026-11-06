package dev.fastlane.ide

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement

/** TextMate PSI may represent the entire file as one leaf, so it cannot be a lane target. */
class LaneNavigationElement(private val file: PsiFile, val declaration: Declaration) : FakePsiElement() {
    override fun getParent() = file
    override fun getContainingFile() = file
    override fun getName() = declaration.name
    override fun getText() = declaration.name
    override fun getTextOffset() = declaration.nameOffset
    override fun getTextRange() = TextRange.from(declaration.nameOffset, declaration.name.length)
    override fun getPresentableText() = declaration.name
    override fun getLocationString() = file.name
    override fun getIcon(unused: Boolean) = AllIcons.Nodes.Method
    override fun isValid() = file.isValid && textRange.endOffset <= file.textLength &&
        file.text.regionMatches(textOffset, declaration.name, 0, declaration.name.length)
    override fun canNavigate() = isValid && file.virtualFile != null
    override fun canNavigateToSource() = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        val virtualFile = file.virtualFile ?: return
        if (isValid) OpenFileDescriptor(project, virtualFile, textOffset).navigate(requestFocus)
    }
    override fun equals(other: Any?) = other is LaneNavigationElement && file == other.file &&
        declaration.nameOffset == other.declaration.nameOffset && declaration.name == other.declaration.name
    override fun hashCode() = 31 * file.hashCode() + declaration.nameOffset
}
