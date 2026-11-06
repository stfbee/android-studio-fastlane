package dev.fastlane.ide

import org.junit.Assert.*
import org.junit.Test

class LaneCallsTest {
    @Test
    fun `calls resolve within platform then fall back to global lanes`() {
        val code = """
            lane :shared do
            end
            platform :android do
              lane :release do
                build
                build()
                shared
                object.build
                value = :build
                values = { build: 1 }
                build_number
                build = 1
              end
              private_lane :build do
                build
              end
            end
            platform :ios do
              lane :release do
                build
                shared()
              end
              lane :build do
              end
            end
        """.trimIndent()
        val nodes = Outline.parse(code, code)
        val calls = LaneCalls.find(code, nodes)
        val android = nodes[1].children.last()
        val ios = nodes[2].children.last()
        assertEquals(3, calls.count { it.targets.single().nameOffset == android.nameOffset })
        assertEquals(1, calls.count { it.targets.single().nameOffset == ios.nameOffset })
        assertEquals(2, calls.count { it.targets.single().nameOffset == nodes[0].nameOffset })
        assertEquals(6, calls.size)
        calls.forEach { call ->
            assertEquals(call.targets.single().name, code.substring(call.range.startOffset, call.range.endOffset))
        }
    }
}
