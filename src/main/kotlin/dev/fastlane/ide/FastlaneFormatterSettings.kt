package dev.fastlane.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.util.ui.FormBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

@State(name = "FastlaneFormatter", storages = [Storage("FastlaneNavigator.xml")])
class FastlaneFormatterSettings : PersistentStateComponent<FastlaneFormatterSettings.Options> {
    data class Options(var rubyExecutable: String = "")
    private var options = Options()
    override fun getState() = options
    override fun loadState(state: Options) { options = state }
    companion object {
        fun getInstance(): FastlaneFormatterSettings = ApplicationManager.getApplication().getService(FastlaneFormatterSettings::class.java)
    }
}

class FastlaneFormatterConfigurable : SearchableConfigurable {
    private var ruby: JTextField? = null
    override fun getId() = "ru.ovm.fastlane.navigator.formatting"
    override fun getDisplayName() = "Fastlane"
    override fun createComponent(): JComponent {
        val field = JTextField()
        ruby = field
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Ruby executable:", field)
            .addComponent(JLabel("Leave empty to detect Ruby automatically. Ruby 2.7 or newer is required."))
            .addComponent(JLabel("Reformat Code formats the entire file using the bundled formatter."))
            .addComponentFillVertically(javax.swing.JPanel(), 0).panel
    }
    override fun reset() { ruby?.text = FastlaneFormatterSettings.getInstance().state.rubyExecutable }
    override fun isModified() = ruby?.text?.trim() != FastlaneFormatterSettings.getInstance().state.rubyExecutable
    override fun apply() {
        val value = ruby?.text?.trim().orEmpty()
        if (value.isNotEmpty() && (!Files.isRegularFile(Path.of(value)) || !Files.isExecutable(Path.of(value)))) {
            throw ConfigurationException("Select an executable Ruby file, or leave the field empty for automatic detection.")
        }
        FastlaneFormatterSettings.getInstance().state.rubyExecutable = value
    }
    override fun disposeUIResources() { ruby = null }
}
