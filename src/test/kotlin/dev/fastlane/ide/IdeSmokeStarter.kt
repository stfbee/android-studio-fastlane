package dev.fastlane.ide

import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.search.LocalSearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.TextMateFileType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** Only packaged into the isolated test plugin, never the distribution. */
class IdeSmokeStarter : ApplicationStarter {
    override val isHeadless = true
    override val requiredModality = ApplicationStarter.NOT_IN_EDT
    override fun main(args: List<String>) {
        val report = Path.of(System.getProperty("fastlane.smoke.report"))
        try {
            // Keep the isolated test project's startup free of the Google sign-in modal.
            com.intellij.ide.util.PropertiesComponent.getInstance()
                .setValue("com.google.gct.login2.ui.StartupSignInDialog.shownVersion", "9999.0")
            val project = com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx()
                .openProject(report.parent.resolve("project"), com.intellij.ide.impl.OpenProjectTask(
                    isNewProject = true, useDefaultProjectAsTemplate = false))
                ?: error("Could not create isolated test project")
            val source = """
                desc "Publish Android"
                platform :android do
                  lane :release do
                    build
                    build()
                    obj.build
                    value = :build
                    value = { build: 1 }
                    puts "build"
                    # build
                    puts "lane :fake_string do; end"
                    # lane :fake_comment do
                    text = <<~MESSAGE
                      lane :fake_heredoc do
                      end
                    MESSAGE
                    regex = /lane :fake_regex do; end/
                    message = %q{lane :fake_percent do; end}
                    work if ready
                    things.each do |thing|
                      work
                    end
                  end
                  private_lane :build do
                  end
                end
                platform :ios do
                  lane :build do
                    build
                  end
                end
            """.trimIndent()
            val lines = mutableListOf<String>()
            var backgroundSearch: Runnable? = null
            TextMateService.getInstance().ensureInitialized()
            val samples = report.parent.resolve("samples")
            Files.createDirectories(samples)
            val virtualFiles = listOf("Fastfile", "Pluginfile", "Gemfile", "Appfile", "Supplyfile").map { name ->
                val path = samples.resolve(name)
                Files.writeString(path, source)
                (LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("No VFS file: $path"))
                    .also { it.refresh(false, false) }
            }
            ReadAction.run<RuntimeException> {
                val registry = FileTypeRegistry.getInstance()
                val iconProvider = com.intellij.ide.FileIconProvider.EP_NAME.extensionList
                    .filterIsInstance<FastlaneFileIconProvider>().single()
                for (virtualFile in virtualFiles) {
                    val type = registry.getFileTypeByFile(virtualFile)
                    check(type.name == "textmate") { "${virtualFile.name} -> ${type.name}" }
                    check(TextMateService.getInstance().getLanguageDescriptorByFileName(virtualFile.name) != null)
                    lines += "${virtualFile.name}: ${type.name}"
                    check(iconProvider.getIcon(virtualFile, 0, project) === FastlaneIcons.FILE)
                }
                check(iconProvider.getIcon(com.intellij.testFramework.LightVirtualFile("ordinary.rb", "puts 1"), 0, project) == null)
                check(iconProvider.getIcon(com.intellij.testFramework.LightVirtualFile("Gemfile.lock", "GEM"), 0, project) == null)
                val logo = com.intellij.openapi.util.IconLoader.getIcon("/META-INF/pluginIcon.svg", FastlaneIcons::class.java)
                check(logo.iconWidth == 40 && logo.iconHeight == 40)
                check(FastlaneIcons.FILE.iconWidth == 16 && FastlaneIcons.FILE.iconHeight == 16)
                val preview = java.awt.image.BufferedImage(640, 176, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val graphics = preview.createGraphics()
                try {
                    for ((index, background) in listOf(java.awt.Color.WHITE, java.awt.Color(0x2b2d30)).withIndex()) {
                        val left = index * 320
                        graphics.color = background
                        graphics.fillRect(left, 0, 320, 176)
                        graphics.color = if (index == 0) java.awt.Color(0x222222) else java.awt.Color(0xdddddd)
                        graphics.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14)
                        graphics.drawString("Fastlane Navigator", left + 20, 24)
                        logo.paintIcon(null, graphics, left + 24, 46)
                        val large = graphics.create() as java.awt.Graphics2D
                        try {
                            large.translate(left + 92, 42)
                            large.scale(2.0, 2.0)
                            logo.paintIcon(null, large, 0, 0)
                        } finally { large.dispose() }
                        FastlaneIcons.FILE.paintIcon(null, graphics, left + 24, 140)
                        graphics.drawString("Fastfile   Pluginfile   Gemfile", left + 48, 153)
                    }
                } finally { graphics.dispose() }
                javax.imageio.ImageIO.write(preview, "png", report.parent.resolve("icons-preview.png").toFile())
                lines += "Icons: plugin logo 40px, file icon 16px, registration, supported files and exclusions verified"
                val nodes = FastlaneAnalysis.analyze(source)
                check(nodes.map { it.name } == listOf("android", "ios")) { "Platforms: $nodes" }
                check(nodes[0].children.map { it.name } == listOf("release", "build")) { "Android lanes: $nodes" }
                check(nodes[1].children.map { it.name } == listOf("build")) { "iOS lanes: $nodes" }
                lines += "Ruby grammar: strings, comments, heredoc, regex and percent literals ignored"
                val file = PsiFileFactory.getInstance(project).createFileFromText("Fastfile", TextMateFileType.INSTANCE, source)
                val builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file)
                check(builder != null) { "Structure extension not registered" }
                lines += "Structure extension: registered for Fastfile"
                val other = PsiFileFactory.getInstance(project).createFileFromText("ordinary.rb", TextMateFileType.INSTANCE, "puts 1")
                check(FastlaneStructureFactory().getStructureViewBuilder(other) == null)
                lines += "Ordinary Ruby files: unchanged"
            }
            // Editor construction and disposal require the UI thread, including in headless mode.
            ApplicationManager.getApplication().invokeAndWait {
                ReadAction.run<RuntimeException> {
                    val file = PsiManager.getInstance(project).findFile(virtualFiles.first()) ?: error("No physical PSI")
                    check(file.text == source) { "Physical PSI has stale sample contents: ${file.textLength} vs ${source.length}" }
                    val factory = EditorFactory.getInstance()
                    val document = FileDocumentManager.getInstance().getDocument(virtualFiles.first()) ?: error("No document")
                    val editor = factory.createEditor(document, project)
                    try {
                        val offset = source.indexOf("    build") + 5
                        val targets = FastlaneGotoDeclaration().getGotoDeclarationTargets(file.findElementAt(offset), offset, editor)
                        check(targets?.single()?.textRange?.contains(nodesOffset(source)) == true) { "Wrong lane target: ${targets?.toList()}" }
                        val target = targets.single()
                        val destination = EditSourceUtil.getDescriptor(target) as? OpenFileDescriptor
                        check(destination?.offset == nodesOffset(source)) {
                            "Navigation jumps to ${destination?.offset}, expected ${nodesOffset(source)}; PSI range=${target.textRange}, textOffset=${target.textOffset}, class=${target.javaClass.name}"
                        }
                        val actionTargets = GotoDeclarationAction.findAllTargetElements(project, editor, offset)
                        check(actionTargets.singleOrNull()?.textOffset == nodesOffset(source)) {
                            "Go to Declaration action did not resolve an exact lane target"
                        }
                        editor.caretModel.moveToOffset(offset)
                        destination.navigateIn(editor)
                        check(editor.caretModel.offset == nodesOffset(source)) {
                            "Editor caret did not move to the lane declaration"
                        }
                        lines += "Go to declaration action and OpenFileDescriptor: exact lane name offset, not file start"
                        val flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                        val targetUtil = TargetElementUtil.getInstance()
                        val evaluator = com.intellij.codeInsight.TargetElementUtilBase.getElementEvaluatorsEx2(file.language)
                        check(evaluator is LaneTargetEvaluator) { "No evaluator for ${file.language}: $evaluator" }
                        val directTarget = evaluator.adjustReferenceOrReferencedElement(file, editor, nodesOffset(source), flags, null)
                        check(directTarget is LaneNavigationElement) { "Direct evaluator cannot find target at ${nodesOffset(source)}: $directTarget" }
                        val declaredLane = targetUtil.findTargetElement(editor, flags, nodesOffset(source))
                        check(declaredLane is LaneNavigationElement) { "Find Usages target on declaration: $declaredLane" }
                        val calledLane = targetUtil.findTargetElement(editor, flags, offset)
                        check(calledLane == declaredLane) { "Find Usages target on call: $calledLane" }
                        val manager = FindUsagesManager(project)
                        check(manager.canFindUsages(declaredLane)) { "Standard Find Usages is unavailable" }
                        val handler = manager.getFindUsagesHandler(declaredLane, false) ?: error("No Find Usages handler")
                        check(handler is LaneFindUsagesHandler) { "Wrong handler: ${handler.javaClass}" }
                        val found = mutableListOf<UsageInfo>()
                        val options = FindUsagesOptions(LocalSearchScope(file)).apply { isUsages = true }
                        check(handler.processElementUsages(declaredLane, Processor { found.add(it); true }, options))
                        val expectedCalls = listOf(source.indexOf("    build") + 4, source.indexOf("    build()") + 4)
                        val reverseTargets = GotoDeclarationAction.findAllTargetElements(project, editor, nodesOffset(source) + 1)
                        check(reverseTargets.map { it.textOffset } == expectedCalls) {
                            "Command-click on declaration resolved to ${reverseTargets.map { it.textOffset }}, expected calls $expectedCalls"
                        }
                        for (reverseTarget in reverseTargets) {
                            val callDestination = EditSourceUtil.getDescriptor(reverseTarget) as? OpenFileDescriptor
                                ?: error("No descriptor for reverse target")
                            callDestination.navigateIn(editor)
                            check(editor.caretModel.offset == reverseTarget.textOffset)
                        }
                        lines += "Command-click on declaration: call targets and exact editor navigation verified"
                        check(found.map { it.navigationOffset } == expectedCalls) {
                            "Find Usages offsets: ${found.map { it.navigationOffset }}, expected $expectedCalls"
                        }
                        val displayed = mutableListOf<com.intellij.usages.Usage>()
                        val searcher = FindUsagesManager.createUsageSearcher(handler, arrayOf(declaredLane), emptyArray(), options)
                        backgroundSearch = Runnable {
                            check(!ApplicationManager.getApplication().isReadAccessAllowed) { "Test must reproduce background Find Usages without an outer read action" }
                            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                                searcher.generate(Processor { displayed.add(it); true })
                            }, com.intellij.openapi.progress.EmptyProgressIndicator())
                            ReadAction.run<RuntimeException> {
                                check(displayed.map { (it as com.intellij.usages.UsageInfo2UsageAdapter).navigationOffset } == expectedCalls) {
                                    "Background usage searcher did not produce the expected navigable results"
                                }
                            }
                            lines += "Find Usages: standard searcher completed on background thread without outer read access"
                        }
                        for (usage in found) {
                            val callOffset = usage.navigationOffset
                            OpenFileDescriptor(project, virtualFiles.first(), callOffset).navigateIn(editor)
                            check(editor.caretModel.offset == callOffset)
                        }
                        val presentation = manager.createPresentation(handler, options)
                        check(presentation.tabText.contains("build"))
                        lines += "Find Usages: standard declaration/call targets, handler, exact result offsets and navigation verified"
                        lines += "Find Usages: iOS calls, receiver calls, symbols, labels, strings and comments excluded from Android build results"
                        val builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file) as com.intellij.ide.structureView.TreeBasedStructureViewBuilder
                        val model = builder.createStructureViewModel(editor)
                        try {
                            check(model.root.children.size == 2)
                            check(model.root.children[0].children.size == 2)
                            lines += "Structure model: platform groups and lane children created"
                        } finally { model.dispose() }
                        lines += "Go to declaration: build resolved to Android private lane, not iOS lane"
                    } finally { factory.releaseEditor(editor) }
                }
            }
            checkNotNull(backgroundSearch).run()
            lines += FormatterSmoke.run(project, report.parent)
            lines += CiNavigationSmoke.run(project, report.parent)
            Files.writeString(report, lines.joinToString("\n", postfix = "\nPASS\n"))
            exitProcess(0)
        } catch (failure: Throwable) {
            Files.writeString(report, failure.stackTraceToString())
            failure.printStackTrace()
            exitProcess(1)
        }
    }
    private fun nodesOffset(source: String) = source.indexOf(":build do") + 1
}
