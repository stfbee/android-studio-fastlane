package dev.fastlane.ide

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.ide.util.EditSourceUtil
import java.nio.file.Files
import java.nio.file.Path

object CiNavigationSmoke {
    fun run(project: Project, folder: Path): List<String> {
        val root = Files.createTempDirectory(folder, "ci-navigation-")
        val fastfile = Files.createDirectories(root.resolve("mobile/fastlane")).resolve("Fastfile")
        val ruby = "default_platform(:android)\nplatform :android do\n  lane :release do\n  end\n  private_lane :secret do\n  end\nend\nplatform :ios do\n  lane :release do\n  end\nend\n"
        Files.writeString(fastfile, ruby)
        val github = Files.createDirectories(root.resolve(".github/workflows")).resolve("publish.yml")
        val gitlab = root.resolve(".gitlab-ci.yml")
        val fixtures = listOf(
            Triple(github, "jobs:\n  publish:\n    steps:\n      - run: bundle exec fastlane android release\n        working-directory: mobile\n", ruby.indexOf(":release") + 1),
            Triple(github, "jobs:\n  publish:\n    steps:\n      - uses: maierj/fastlane-action@v3.2.0\n        with:\n          lane: android release\n          subdirectory: mobile\n", ruby.indexOf(":release") + 1),
            Triple(gitlab, "publish:\n  script:\n    - cd mobile\n    - fastlane ios release\n", ruby.lastIndexOf(":release") + 1),
            Triple(gitlab, ".fastlane:\n  before_script:\n    - cd mobile\npublish:\n  extends: .fastlane\n  script:\n    - bundle exec fastlane android release\n", ruby.indexOf(":release") + 1),
            Triple(gitlab, "publish:\n  script:\n    - cd mobile\n    - fastlane release\n", ruby.indexOf(":release") + 1),
        )
        // Keep the source document unsaved to verify that navigation uses the current editor text.
        for ((path, text, expected) in fixtures) {
            Files.writeString(path, "# saved content\n")
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fastfile) ?: error("No Fastfile")
            val virtual = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("No CI file")
            ApplicationManager.getApplication().invokeAndWait {
                WriteCommandAction.runWriteCommandAction(project, Runnable {
                    val document = FileDocumentManager.getInstance().getDocument(virtual) ?: error("No CI document")
                    document.setText(text)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    val factory = EditorFactory.getInstance()
                    val editor = factory.createEditor(document, project)
                    try {
                        val offset = text.indexOf("release") + 2
                        val targets = GotoDeclarationAction.findAllTargetElements(project, editor, offset)
                        val target = targets.singleOrNull() ?: error("Expected one CI navigation target: ${targets.toList()}")
                        check(target is LaneNavigationElement && target.textOffset == expected)
                        check(target.containingFile.virtualFile.toNioPath() == fastfile)
                        val descriptor = EditSourceUtil.getDescriptor(target) as? OpenFileDescriptor ?: error("No navigation descriptor")
                        val targetDocument = FileDocumentManager.getInstance().getDocument(target.containingFile.virtualFile)!!
                        val targetEditor = factory.createEditor(targetDocument, project)
                        try {
                            descriptor.navigateIn(targetEditor)
                            check(targetEditor.caretModel.offset == expected) { "CI navigation went to the wrong offset" }
                        } finally { factory.releaseEditor(targetEditor) }
                        if ("fastlane release" in text) {
                            val alternative = fastfile.resolveSibling("Fastfile.rb")
                            Files.writeString(alternative, ruby)
                            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(alternative) ?: error("No second Fastfile")
                            val choices = GotoDeclarationAction.findAllTargetElements(project, editor, offset)
                            check(choices.size == 2 && choices.all { it.textOffset == expected }) { "Ambiguous Fastfiles were not offered as separate targets" }
                            check(choices.map { it.containingFile.virtualFile.toNioPath() }.toSet() == setOf(fastfile, alternative))
                        }
                        document.setText(text.replace("release", "secret"))
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                        check(GotoDeclarationAction.findAllTargetElements(project, editor, document.text.indexOf("secret") + 1).isEmpty()) {
                            "Private lane was exposed to the CLI"
                        }
                    } finally { factory.releaseEditor(editor) }
                })
            }
        }
        return listOf("CI navigation: standard Go to Declaration resolves unsaved GitHub/GitLab commands, maierj action inputs and inherited GitLab jobs to exact lane offsets; private lanes excluded; multiple Fastfiles offered as distinct targets") + realProject(project)
    }

    /** Optional read-only regression check against a local checkout, never packaged into the plugin. */
    private fun realProject(project: Project): List<String> {
        val path = System.getenv("FASTLANE_CI_PROJECT")?.takeIf { it.isNotBlank() } ?: return emptyList()
        val root = Path.of(path)
        val workflows = Files.list(root.resolve(".github/workflows")).use { files ->
            files.filter { it.fileName.toString().endsWith(".yml") || it.fileName.toString().endsWith(".yaml") }.toList()
        }
        val counts = mutableMapOf(CiLaneCommands.Kind.GITHUB to 0, CiLaneCommands.Kind.GITLAB to 0)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve("fastlane/Fastfile")) ?: error("No real Fastfile")
        for (path in workflows + listOf(root.resolve(".gitlab-ci.yml"))) {
            val kind = if (path.fileName.toString() == ".gitlab-ci.yml") CiLaneCommands.Kind.GITLAB else CiLaneCommands.Kind.GITHUB
            val virtual = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("No real CI file")
            ApplicationManager.getApplication().invokeAndWait {
                com.intellij.openapi.application.ReadAction.run<RuntimeException> {
                    val document = FileDocumentManager.getInstance().getDocument(virtual) ?: error("No real CI document")
                    val calls = CiLaneCommands.find(document.text, kind).filter { it.directory != null }
                    if (calls.isNotEmpty()) {
                        val factory = EditorFactory.getInstance()
                        val editor = factory.createEditor(document, project)
                        try {
                            for (call in calls) {
                                val targets = GotoDeclarationAction.findAllTargetElements(project, editor, call.start + 1)
                                check(targets.isNotEmpty() && targets.all { it is LaneNavigationElement && it.text == call.lane && it.textOffset > 0 }) {
                                    "Real CI navigation failed: ${path.fileName}:${call.lane}"
                                }
                                counts[kind] = counts.getValue(kind) + 1
                            }
                        } finally { factory.releaseEditor(editor) }
                    }
                }
            }
        }
        check(counts.values.all { it > 0 }) { "Real CI regression did not exercise both providers: $counts" }
        return listOf("Real checkout (read-only): ${counts.getValue(CiLaneCommands.Kind.GITHUB)} GitHub and ${counts.getValue(CiLaneCommands.Kind.GITLAB)} GitLab lane navigation targets verified")
    }
}
