plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "dev.fastlane"
version = "0.1.4"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly(kotlin("stdlib"))
    intellijPlatform {
        local(providers.gradleProperty("studioPath").orElse(
            providers.environmentVariable("ANDROID_STUDIO_HOME").orElse(
                "${System.getProperty("user.home")}/Applications/Android Studio.app/Contents")))
        bundledPlugin("org.jetbrains.plugins.textmate")
    }
}

kotlin { jvmToolchain(25) }
tasks.compileKotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
tasks.compileTestKotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
tasks.compileJava { options.release.set(21) }
tasks.compileTestJava { options.release.set(21) }

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
}

tasks.prepareSandbox {
    from("bundles") { into("${rootProject.name}/bundles") }
}
