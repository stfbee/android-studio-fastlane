package dev.fastlane.ide

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement

/** A reverse-navigation target points to the call, not its lane declaration. */
class LaneCallNavigationElement(private val file: PsiFile, private val laneName: String, private val range: TextRange) : FakePsiElement() {
    override fun getParent() = file
    override fun getContainingFile() = file
    override fun getName() = laneName
    override fun getText() = laneName
    override fun getTextOffset() = range.startOffset
    override fun getTextRange() = range
    override fun getPresentableText(): String {
        val text = file.text
        val start = text.lastIndexOf('\n', (textOffset - 1).coerceAtLeast(0)) + 1
        val end = text.indexOf('\n', textOffset).let { if (it < 0) text.length else it }
        return text.substring(start, end).trim()
    }
    override fun getLocationString() = "${file.name}:${file.text.take(textOffset).count { it == '\n' } + 1}"
    override fun getIcon(unused: Boolean) = AllIcons.Nodes.Method
    override fun isValid() = file.isValid && range.endOffset <= file.textLength &&
        file.text.regionMatches(textOffset, laneName, 0, laneName.length)
    override fun canNavigate() = isValid && file.virtualFile != null
    override fun canNavigateToSource() = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        val virtualFile = file.virtualFile ?: return
        if (isValid) OpenFileDescriptor(project, virtualFile, textOffset).navigate(requestFocus)
    }
}
