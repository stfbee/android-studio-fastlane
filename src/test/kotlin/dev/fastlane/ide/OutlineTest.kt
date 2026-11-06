package dev.fastlane.ide

class OutlineTest {
    @org.junit.Test
    fun staticOutlineScenarios() = main()
}

fun main() {
    var checks = 0
    fun verify(source: String, expected: List<String>) {
        val parsed = Outline.parse(source, source)
        val actual = Outline.flatten(parsed).map { "${it.kind}:${it.name}" }
        check(actual == expected) { "Expected $expected, got $actual\n$source" }
        checks++
    }
    verify("""
        lane :global do
        end
        platform :android do
          lane :release do
            items.each do |item|
              if item
                upload
              end
            end
          end
          private_lane :build do
          end
          override_lane :deploy do
          end
        end
        platform :ios do
          lane :release do
          end
        end
    """.trimIndent(), listOf("lane:global", "platform:android", "lane:release", "private_lane:build", "override_lane:deploy", "platform:ios", "lane:release"))

    val boundaries = """
        platform :android do
          lane :first do
            upload if ready
            upload unless skipped
            work while pending
            work until done
            while active do
              work
            end
            for item in items do
              work
            end
            x = if ready
              1
            end
          end
          lane :second do
          end
        end
        lane :outside do
        end
    """.trimIndent()
    val parsed = Outline.parse(boundaries, boundaries)
    check(parsed.map { it.name } == listOf("android", "outside"))
    check(parsed[0].children.map { it.name } == listOf("first", "second"))
    check(parsed[0].endOffset == boundaries.indexOf("\nlane :outside") - 0)
    checks++

    verify("""
        lane(:release) do
        end
        lane(
          :debug
        ) do
        end
        lane :unfinished do
    """.trimIndent(), listOf("lane:release", "lane:debug", "lane:unfinished"))

    verify("""
        def helper
          lane :not_a_declaration do
          end
        end
        def value = 1
        lane :real do
          values = { end: 1, do: 2 }
          obj.end
          obj.do
        end
        lane :next_lane do
        end
    """.trimIndent(), listOf("lane:real", "lane:next_lane"))

    val literals = """
        desc "Publish build"
        lane "release" do
          puts "lane :fake do; end"
        end
    """.trimIndent()
    val masked = literals.toCharArray()
    Regex("\"[^\"]*\"").findAll(literals).forEach { match ->
        for (i in match.range) masked[i] = ' '
    }
    val literalNodes = Outline.parse(literals, String(masked))
    check(literalNodes.single().name == "release")
    check(literalNodes.single().description == "Publish build")
    check(literals.substring(literalNodes.single().nameOffset).startsWith("release"))
    checks++
    println("Outline tests: $checks scenarios passed")
}
