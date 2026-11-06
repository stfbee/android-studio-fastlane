package dev.fastlane.ide

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.psi.PsiFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class FastlaneFormattingService : AsyncDocumentFormattingService(), PluginAware {
    private lateinit var plugin: PluginDescriptor
    override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) { plugin = pluginDescriptor }
    override fun getName() = "Fastlane"
    override fun getNotificationGroupId() = "Fastlane formatting"
    override fun getFeatures() = setOf(FormattingService.Feature.AD_HOC_FORMATTING)
    override fun canFormat(file: PsiFile) = FastlaneFiles.isSupported(file.name) && file.fileType.name == "textmate"
    override fun getTimeout(): Duration = Duration.ofSeconds(20)
    override fun prepareForFormatting(document: com.intellij.openapi.editor.Document, context: com.intellij.formatting.FormattingContext) {
        // The formatter consumes stdin, so the unsaved editor buffer must not be saved first.
    }

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        // Typing/indent-only requests must never launch a whole-file formatter.
        if (request.canChangeWhitespaceOnly() || request.isQuickFormat) {
            request.onTextReady(request.documentText)
            return null
        }
        val executable = RubyExecutable.find(FastlaneFormatterSettings.getInstance().state.rubyExecutable)
        if (executable == null) {
            request.onError("Ruby not found", "Set the Ruby executable in Settings > Languages & Frameworks > Fastlane. Ruby 2.7 or newer is required.")
            return null
        }
        val script = plugin.pluginPath.resolve("formatter/format.rb")
        val file = request.context.containingFile
        val width = request.context.codeStyleSettings.getRightMargin(file.language).coerceAtLeast(20)
        val workDirectory = request.ioFile?.parentFile
        val text = request.documentText
        return object : FormattingTask {
            private val cancelled = AtomicBoolean(false)
            private val process = AtomicReference<CapturingProcessHandler?>()
            override fun isRunUnderProgress() = true
            override fun run() {
                // Some IDE callers request synchronous formatting on the UI thread.
                // The process itself must still run on a worker; completion is delivered via request callbacks.
                com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().execute { formatText() }
            }
            private fun formatText() {
                if (cancelled.get()) return
                try {
                    val command = GeneralCommandLine(executable, "--disable-gems", script.toString(), width.toString())
                        .withCharset(StandardCharsets.UTF_8)
                        .withEnvironment("RUBYOPT", "")
                        .withEnvironment("RUBYLIB", "")
                    if (workDirectory != null) command.withWorkDirectory(workDirectory)
                    val handler = CapturingProcessHandler(command)
                    process.set(handler)
                    if (cancelled.get()) { handler.destroyProcess(); return }
                    handler.processInput.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
                    val result = handler.runProcess(15_000, true)
                    if (cancelled.get()) return
                    when {
                        result.isTimeout -> request.onError("Formatting timed out", "Ruby formatting took longer than 15 seconds. The document was kept unchanged.")
                        result.exitCode != 0 -> request.onError("Fastlane formatting failed", result.stderr.trim().take(3000).ifEmpty { "Ruby exited with code ${result.exitCode}. The document was kept unchanged." })
                        else -> request.onTextReady(result.stdout)
                    }
                } catch (cancelled: com.intellij.openapi.progress.ProcessCanceledException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (!cancelled.get()) request.onError("Fastlane formatting failed", "Check the Ruby executable in Settings > Languages & Frameworks > Fastlane. ${failure.message.orEmpty().take(1000)}")
                }
            }
            override fun cancel(): Boolean {
                cancelled.set(true)
                process.get()?.destroyProcess()
                return true
            }
        }
    }
}

object RubyExecutable {
    fun find(configured: String): String? {
        if (configured.isNotBlank()) return configured
        val ruby = if (com.intellij.openapi.util.SystemInfo.isWindows) "ruby.exe" else "ruby"
        val candidates = PathEnvironmentVariableUtil.findAllExeFilesInPath(ruby).map { it.toPath() }.toMutableList()
        val home = System.getProperty("user.home")
        candidates += listOf(
            Path.of(home, ".rbenv/shims/ruby"), Path.of(home, ".asdf/shims/ruby"),
            Path.of(home, ".local/share/mise/shims/ruby"), Path.of("/opt/homebrew/bin/ruby"),
            Path.of("/usr/local/bin/ruby"), Path.of("/usr/bin/ruby"),
        )
        // macOS's system Ruby is older than the formatter's minimum; prefer other installations.
        return candidates.distinct().sortedBy { if (it.toString() == "/usr/bin/ruby") 1 else 0 }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }?.toString()
    }
}
