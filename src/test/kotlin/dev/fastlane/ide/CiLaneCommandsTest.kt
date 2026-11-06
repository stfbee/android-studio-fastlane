package dev.fastlane.ide

import org.junit.Assert.*
import org.junit.Test

class CiLaneCommandsTest {
    private fun github(commands: String) = """
        name: fastlane release
        jobs:
          publish:
            runs-on: ubuntu-latest
            steps:
              - name: fastlane release
                run: |
    """.trimIndent() + "\n" + commands.prependIndent("          ") + "\n"

    @Test fun `literal commands have exact lane ranges`() {
        val text = github("bundle exec fastlane android release\nfastlane 'upload' track:beta\nfastlane \"notify\"")
        val calls = CiLaneCommands.find(text, CiLaneCommands.Kind.GITHUB)
        assertEquals(listOf("release", "upload", "notify"), calls.map { it.lane })
        assertEquals(listOf("android", null, null), calls.map { it.platform })
        calls.forEach { assertEquals(it.lane, text.substring(it.start, it.end)) }
    }

    @Test fun `comments echoed text and dynamic names are ignored`() {
        val text = github("# fastlane release\necho 'fastlane release'\nfastlane ${'$'}LANE\nfastlane android ${'$'}LANE\n" +
            "echo \"fastlane android release\"\nfastlane ${'$'}{{ inputs.lane }}")
        assertTrue(CiLaneCommands.find(text, CiLaneCommands.Kind.GITHUB).isEmpty())
    }

    @Test fun `working directory and cd are tracked per step`() {
        val text = """
            defaults:
              run:
                working-directory: mobile
            jobs:
              publish:
                steps:
                  - run: bundle exec fastlane android release
                    working-directory: apps
                  - run: |
                      cd android && fastlane upload
                      fastlane notify
                  - run: fastlane clean
        """.trimIndent()
        val calls = CiLaneCommands.find(text, CiLaneCommands.Kind.GITHUB)
        assertEquals(listOf("apps", "mobile/android", "mobile/android", "mobile"), calls.map { it.directory })
    }

    @Test fun `gitlab scripts inherit before script directory`() {
        val text = """
            before_script:
              - cd mobile
            publish:
              script:
                - bundle exec fastlane android release
                - cd child
                - 'fastlane upload'
            other:
              before_script: []
              script: fastlane clean
        """.trimIndent()
        val calls = CiLaneCommands.find(text, CiLaneCommands.Kind.GITLAB)
        assertEquals(listOf("release", "upload", "clean"), calls.map { it.lane })
        assertEquals(listOf("./mobile", "./mobile/child", "."), calls.map { it.directory })
        calls.forEach { assertEquals(it.lane, text.substring(it.start, it.end)) }
    }

    @Test fun `dynamic workdir does not fall back to root`() {
        val text = """
            jobs:
              publish:
                steps:
                  - run: fastlane release
                    working-directory: ${'$'}{{ inputs.path }}
        """.trimIndent()
        assertNull(CiLaneCommands.find(text, CiLaneCommands.Kind.GITHUB).single().directory)
    }

    @Test fun `heredocs aliases and missing parents are not guessed`() {
        assertTrue(CiLaneCommands.find(github("cat <<EOF\nfastlane release\nEOF"), CiLaneCommands.Kind.GITHUB).isEmpty())
        val text = "job:\n  extends: .base\n  script:\n    - fastlane release\nother:\n  script: *commands\n"
        assertNull(CiLaneCommands.find(text, CiLaneCommands.Kind.GITLAB).single().directory)
    }

    @Test fun `fastlane action inputs have exact ranges and their own subdirectory`() {
        val text = """
            defaults:
              run:
                working-directory: ignored-for-actions
            jobs:
              publish:
                steps:
                  - uses: maierj/fastlane-action@v3.2.0
                    with:
                      lane: build_debug_apps
                  - uses: maierj/fastlane-action@v3.2.0
                    with:
                      lane: 'android release'
                      subdirectory: mobile
                  - uses: maierj/fastlane-action@v3.2.0
                    with:
                      lane: fastlane bump_version_code
                  - uses: unrelated/action@v1
                    with:
                      lane: ignored
        """.trimIndent()
        val calls = CiLaneCommands.find(text, CiLaneCommands.Kind.GITHUB)
        assertEquals(listOf("build_debug_apps", "release", "bump_version_code"), calls.map { it.lane })
        assertEquals(listOf(null, "android", null), calls.map { it.platform })
        assertEquals(listOf(".", "mobile", "."), calls.map { it.directory })
        calls.forEach { assertEquals(it.lane, text.substring(it.start, it.end)) }
    }

    @Test fun `same file extends and references preserve inherited directory`() {
        val text = """
            .setup:
              before_script:
                - cd mobile
            .fastlane:
              extends: .setup
            .prepare:
              script:
                - cd android
            upload:
              extends: .fastlane
              script:
                - !reference [.prepare, script]
                - bundle exec fastlane delivery_debug_apps_to_firebase
        """.trimIndent()
        val call = CiLaneCommands.find(text, CiLaneCommands.Kind.GITLAB).single()
        assertEquals("delivery_debug_apps_to_firebase", call.lane)
        assertEquals("./mobile/android", call.directory)
        assertEquals(call.lane, text.substring(call.start, call.end))
    }

    @Test fun `last parent wins local overrides win and cycles stop`() {
        val text = """
            .a:
              before_script: cd first
            .b:
              before_script: cd last
            .cycle:
              extends: .cycle
            upload:
              extends: [.a, .b]
              script: fastlane release
            local:
              extends: .a
              before_script: []
              script: fastlane notify
            broken:
              extends: .cycle
              script: fastlane clean
        """.trimIndent()
        val calls = CiLaneCommands.find(text, CiLaneCommands.Kind.GITLAB)
        assertEquals(listOf("./last", ".", null), calls.map { it.directory })
    }
}
