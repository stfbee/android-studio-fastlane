package dev.fastlane.ide

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.nio.file.Files
import java.nio.file.Path

object FormatterSmoke {
    fun run(project: Project, folder: Path): List<String> {
        val directory = Files.createDirectories(folder.resolve("formatting-samples"))
        val service = FormattingService.EP_NAME.extensionList.filterIsInstance<FastlaneFormattingService>().single()
        val samples = mapOf(
            "Fastfile" to ("platform :android do\nlane :release do\ngradle(task:\"assemble\")\nend\nend\n" to
                "platform :android do\n  lane :release do\n    gradle(task: \"assemble\")\n  end\nend\n"),
            "Pluginfile" to ("gem   \"fastlane-plugin-example\"\n" to "gem \"fastlane-plugin-example\"\n"),
            "Gemfile" to ("source   \"https://rubygems.org\"\ngem   \"fastlane\"\n" to "source \"https://rubygems.org\"\ngem \"fastlane\"\n"),
            "Appfile" to ("app_identifier(  \"ru.example.app\"  )\n" to "app_identifier(\"ru.example.app\")\n"),
        )
        var requestFile: PsiFile? = null
        for ((name, texts) in samples) {
            val path = directory.resolve(name)
            Files.writeString(path, "# on disk, not the editor buffer\n")
            val virtual = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("No sample file")
            virtual.refresh(false, false)
            ApplicationManager.getApplication().invokeAndWait {
                WriteCommandAction.runWriteCommandAction(project, Runnable {
                    val file = PsiManager.getInstance(project).findFile(virtual) ?: error("No sample PSI")
                    val document = FileDocumentManager.getInstance().getDocument(virtual) ?: error("No sample document")
                    document.setText(texts.first)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    check(FormattingServiceUtil.findService(file, true, true) === service) { "Reformat Code did not select Fastlane formatter for $name" }
                    check(FormattingServiceUtil.findService(file, false, true) === service) { "Automatic whole-file formatting is unavailable for $name" }
                    document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, true)
                    try {
                        CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
                        check(document.text == texts.second) { "Reformat Code failed for $name:\n${document.text}" }
                        CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
                        check(document.text == texts.second) { "Second Reformat Code changed $name again" }
                    } finally { document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, null) }
                    requestFile = file
                })
            }
            check(Files.readString(path) == "# on disk, not the editor buffer\n") { "Formatting wrote to disk instead of the document" }
        }

        val file = checkNotNull(requestFile)
        val invalid = TestRequest(file, "lane :broken do\n")
        val invalidTask = ReadAction.compute<AsyncDocumentFormattingService.FormattingTask?, RuntimeException> {
            AsyncDocumentFormattingService.createFormattingTask(service, invalid)
        }
        checkNotNull(invalidTask).run()
        invalid.awaitResult()
        check(invalid.output == null && invalid.error?.contains("Ruby syntax error") == true) { "Invalid Ruby was not rejected: ${invalid.error}" }

        val cancelled = TestRequest(file, "gem   \"fastlane\"\n")
        val cancelledTask = ReadAction.compute<AsyncDocumentFormattingService.FormattingTask?, RuntimeException> {
            AsyncDocumentFormattingService.createFormattingTask(service, cancelled)
        }
        checkNotNull(cancelledTask).cancel()
        cancelledTask.run()
        check(cancelled.output == null && cancelled.error == null) { "Cancelled formatting produced a result" }

        val settings = FastlaneFormatterSettings.getInstance().state
        val previousRuby = settings.rubyExecutable
        try {
            settings.rubyExecutable = directory.resolve("missing-ruby").toString()
            val missing = TestRequest(file, "gem \"fastlane\"\n")
            val task = ReadAction.compute<AsyncDocumentFormattingService.FormattingTask?, RuntimeException> {
                AsyncDocumentFormattingService.createFormattingTask(service, missing)
            }
            checkNotNull(task).run()
            missing.awaitResult()
            check(missing.output == null && missing.error?.contains("Ruby executable") == true)
        } finally { settings.rubyExecutable = previousRuby }

        return listOf(
            "Formatting: standard CodeStyleManager/Reformat Code formatted unsaved Fastfile, Pluginfile, Gemfile and Appfile; results are idempotent and disk contents unchanged",
            "Formatting: syntax errors, missing Ruby and cancellation preserve the original text",
        )
    }

    private class TestRequest(private val file: PsiFile, private val source: String) : AsyncFormattingRequest {
        private val completed = java.util.concurrent.CountDownLatch(1)
        var output: String? = null
        var error: String? = null
        override fun getDocumentText() = source
        override fun getIOFile() = file.virtualFile.toNioPath().toFile()
        override fun getFormattingRanges() = listOf(TextRange(0, source.length))
        override fun canChangeWhitespaceOnly() = false
        override fun isQuickFormat() = false
        override fun getContext(): FormattingContext = FormattingContext.create(file, CodeStyle.getSettings(file))
        fun awaitResult() { check(completed.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "Formatter did not complete" } }
        override fun onTextReady(updatedText: String?) { output = updatedText; completed.countDown() }
        override fun onError(title: String, message: String) { error = "$title: $message"; completed.countDown() }
        override fun onError(title: String, message: String, offset: Int) { onError(title, message) }
    }
}
