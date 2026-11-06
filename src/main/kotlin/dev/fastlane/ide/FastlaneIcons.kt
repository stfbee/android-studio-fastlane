package dev.fastlane.ide

import com.intellij.ide.FileIconProvider
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

object FastlaneIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/fastlaneFile.svg", FastlaneIcons::class.java)
}

/** Covers both VFS icons (tabs/project tree) and PSI icons (navigation/search). */
class FastlaneFileIconProvider : IconProvider(), FileIconProvider, DumbAware {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? =
        if (!file.isDirectory && FastlaneFiles.isSupported(file.name)) FastlaneIcons.FILE else null

    override fun getIcon(element: PsiElement, flags: Int): Icon? =
        if (element is PsiFile && FastlaneFiles.isSupported(element.name)) FastlaneIcons.FILE else null
}
