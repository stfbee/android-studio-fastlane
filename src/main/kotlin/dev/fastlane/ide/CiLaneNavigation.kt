package dev.fastlane.ide

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class CiLaneNavigation : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val source = sourceElement?.containingFile ?: return null
        val virtual = source.virtualFile ?: return null
        val (kind, root) = context(virtual) ?: return null
        val call = CiLaneCommands.find(source.text, kind).firstOrNull { offset in it.start..it.end } ?: return null
        val directory = call.directory ?: return null
        if (directory.startsWith('/') || directory.startsWith('~')) return null
        val working = root.findFileByRelativePath(directory) ?: return null
        if (!VfsUtilCore.isAncestor(root, working, false)) return null
        val project = source.project
        val candidates = mutableListOf<VirtualFile>()
        var folder: VirtualFile? = working
        while (folder != null && VfsUtilCore.isAncestor(root, folder, false)) {
            for (name in listOf("fastlane/Fastfile", "fastlane/Fastfile.rb", "Fastfile", "Fastfile.rb")) {
                folder.findFileByRelativePath(name)?.takeUnless { it.isDirectory }?.let(candidates::add)
            }
            if (candidates.isNotEmpty()) break
            folder = folder.parent
        }
        // A monorepo may contain several Fastfiles. Return all matches so the IDE offers a choice.
        if (candidates.isEmpty() && !DumbService.isDumb(project)) {
            for (name in listOf("Fastfile", "Fastfile.rb")) {
                candidates += FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
                    .filter { VfsUtilCore.isAncestor(working, it, false) }
            }
        }
        return candidates.distinct().sortedBy { it.path }.flatMap { file ->
            val psi = PsiManager.getInstance(project).findFile(file) ?: return@flatMap emptyList()
            val declarations = FastlaneAnalysis.declarations(psi)
            val platform = call.platform ?: Regex("\\bdefault_platform\\s*\\(?\\s*:([A-Za-z_][A-Za-z_0-9]*)")
                .find(FastlaneAnalysis.codeOnly(psi.text))?.groupValues?.get(1)
            val local = declarations.filter { it.kind == "platform" && it.name == platform }
                .flatMap { it.children }.filter { it.name == call.lane && it.kind != "private_lane" }
            val targets = local.ifEmpty { declarations.filter { it.kind in setOf("lane", "override_lane") && it.name == call.lane } }
            targets.map<Declaration, PsiElement> { LaneNavigationElement(psi, it) }
        }.toTypedArray().takeIf { it.isNotEmpty() }
    }

    private fun context(file: VirtualFile): Pair<CiLaneCommands.Kind, VirtualFile>? {
        if (file.extension !in setOf("yml", "yaml")) return null
        val folder = file.parent ?: return null
        if (folder.name == "workflows" && folder.parent?.name == ".github") {
            return CiLaneCommands.Kind.GITHUB to (folder.parent?.parent ?: return null)
        }
        if (file.name == ".gitlab-ci.yml") return CiLaneCommands.Kind.GITLAB to folder
        return null
    }
}
