# frozen_string_literal: true

require "open3"
require "rbconfig"
require "ripper"

script = File.join(__dir__, "format.rb")
checks = 0
format = lambda do |source, width = 120|
  output, error, status = Open3.capture3(RbConfig.ruby, "--disable-gems", script, width.to_s, stdin_data: source)
  raise "Formatter failed: #{error}" unless status.success?
  raise "Result is invalid Ruby" unless Ripper.sexp(output)
  output
end

{
  'platform :android do\nlane :release do\ngradle(task:"assemble",properties:{"key"=>"value"})\nend\nend\n' =>
    'platform :android do\n  lane :release do\n    gradle(task: "assemble", properties: { "key" => "value" })\n  end\nend\n',
  'source "https://rubygems.org"\ngem "fastlane", "~> 2.0"\n' =>
    'source "https://rubygems.org"\ngem "fastlane", "~> 2.0"\n',
  'gem   "fastlane-plugin-example"\n' => 'gem "fastlane-plugin-example"\n',
  'app_identifier("ru.example.app")\n' => 'app_identifier("ru.example.app")\n'
}.each do |input, expected|
  source = input.gsub('\\n', "\n")
  result = format.call(source)
  raise "Unexpected layout:\n#{result}" unless result == expected.gsub('\\n', "\n")
  raise "Formatter is not idempotent" unless format.call(result) == result
  checks += 1
end

heredoc = <<~'RUBY'
  # Комментарий не теряется
  lane :notify do
  message = <<~MESSAGE
    Привет, #{ENV["NAME"]}!
      Отступ внутри сообщения важен.
  MESSAGE
  puts(message)
  end
RUBY
result = format.call(heredoc)
strings = lambda { |source| Ripper.lex(source).select { |(_, type, _, _)| type == :on_tstring_content }.map { |(_, _, value, _)| value } }
raise "String/heredoc content changed" unless strings.call(heredoc) == strings.call(result)
raise "Comment lost" unless result.include?("# Комментарий не теряется")
checks += 1

guard = "raise 'Fastfile was executed'\nlane :release do\nend\n"
raise "Source was not formatted" unless format.call(guard).include?("Fastfile was executed")
checks += 1

_, error, status = Open3.capture3(RbConfig.ruby, "--disable-gems", script, "120", stdin_data: "lane :broken do\n")
raise "Invalid Ruby was accepted" unless status.exitstatus == 2 && error.include?("Ruby syntax error")
checks += 1

long = 'action(first: "aaaaaaaaaaaaaaaaaaaaaaaa", second: "bbbbbbbbbbbbbbbbbbbbbbbb")' + "\n"
raise "Line width ignored" unless format.call(long, 40).lines.size > format.call(long, 120).lines.size
checks += 1

raw_literal = <<~'LITERAL'.chomp
  """
      Branch: #{branch}
      SHA: #{sha}
      """
LITERAL
raw_source = "lane :publish do\nrelease_notes=#{raw_literal}\nputs(release_notes)\nend\n"
raw_result = format.call(raw_source)
raise "Triple-quoted multiline string was rewritten:\n#{raw_result}" unless raw_result.include?(raw_literal)
raise "Multiline-string formatting is not idempotent" unless format.call(raw_result) == raw_result
checks += 1

[
  "\"Первая строка\n  Вторая строка \\\"в кавычках\\\"\n\"",
  "'Первая строка\n  Вторая строка с \\\\ и " + '#{name}' + "\n'",
  "'''Первая строка\n  Вторая строка\n'''",
  '%q{Первая строка' + "\n  Вторая строка с \\\\ и \"кавычками\"\n}",
  '%Q{Привет, #{name}!' + "\n    Сохранить отступ.\n}",
  '"начало" \\' + "\n    \"продолжение\\n\"",
  "`printf 'first\\n'\nprintf 'second\\n'`",
].each do |literal|
  source = "lane :notify do\nmessage=#{literal} # trailing comment\nputs(message)\nend\n"
  result = format.call(source)
  raise "Multiline literal changed:\n#{result}" unless result.include?(literal)
  raise "Trailing comment lost" unless result.include?("# trailing comment")
  raise "Multiline formatting is not idempotent" unless format.call(result) == result
  checks += 1
end
puts "Formatter tests: #{checks} scenarios passed"
