package dev.fastlane.ide

import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider

object FastlaneFiles {
    fun isFastfile(name: String) = name == "Fastfile" || name == "Fastfile.rb"

    private val supportedNames = setOf(
        "Fastfile", "Fastfile.rb", "Pluginfile", "Gemfile", "Appfile", "Deliverfile", "Matchfile",
        "Scanfile", "Snapfile", "Gymfile", "Screengrabfile", "Supplyfile", "Certfile", "Sighfile",
        "Pemfile", "Pilotfile", "Framefile",
    )
    fun isSupported(name: String) = name in supportedNames
}

class FastlaneBundleProvider : TextMateBundleProvider, PluginAware {
    private lateinit var descriptor: PluginDescriptor
    override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) { descriptor = pluginDescriptor }
    override fun getBundles() = listOf(TextMateBundleProvider.PluginBundle(
        "Fastlane Ruby", descriptor.pluginPath.resolve("bundles/fastlane")))
}
