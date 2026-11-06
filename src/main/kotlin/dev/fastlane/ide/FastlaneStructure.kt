package dev.fastlane.ide

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.*
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiFile
import javax.swing.Icon

class FastlaneStructureFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (!FastlaneFiles.isFastfile(psiFile.name)) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                object : StructureViewModelBase(psiFile, editor, OutlineElement(psiFile, null)) {
                    override fun getSorters() = arrayOf(Sorter.ALPHA_SORTER)
                    override fun getCurrentEditorElement(): Any? {
                        val offset = editor?.caretModel?.offset ?: return psiFile
                        val node = Outline.flatten(FastlaneAnalysis.declarations(psiFile))
                            .lastOrNull { offset in it.offset until it.endOffset }
                        return node ?: psiFile
                    }
                }
        }
    }
}

private class OutlineElement(private val file: PsiFile, private val node: Declaration?) : StructureViewTreeElement {
    override fun getValue(): Any = node ?: file
    override fun getChildren(): Array<StructureViewTreeElement> =
        (node?.children ?: FastlaneAnalysis.declarations(file)).map { OutlineElement(file, it) }.toTypedArray()

    override fun getPresentation() = object : ItemPresentation {
        override fun getPresentableText() = node?.let {
            if (it.kind == "platform") it.name else "${it.name}${when (it.kind) {
                "private_lane" -> " (private)"
                "override_lane" -> " (override)"
                else -> ""
            }}"
        } ?: file.name
        override fun getLocationString() = node?.description
        override fun getIcon(unused: Boolean): Icon = when (node?.kind) {
            "platform" -> AllIcons.Nodes.Package
            "private_lane" -> AllIcons.Nodes.Private
            null -> FastlaneIcons.FILE
            else -> AllIcons.Nodes.Method
        }
    }
    override fun navigate(requestFocus: Boolean) {
        val virtualFile = file.virtualFile ?: return
        OpenFileDescriptor(file.project, virtualFile, node?.nameOffset ?: 0).navigate(requestFocus)
    }
    override fun canNavigate() = file.isValid && file.virtualFile != null
    override fun canNavigateToSource() = canNavigate()
}
