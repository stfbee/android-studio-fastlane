# frozen_string_literal: true

# Invoked with --disable-gems: only the bundled formatter and Ruby stdlib are loaded.
if (RUBY_VERSION.split(".").map(&:to_i).first(2) <=> [2, 7]) == -1
  warn "Formatting requires Ruby 2.7 or newer. Select a newer Ruby in Settings > Languages & Frameworks > Fastlane."
  exit 3
end

$LOAD_PATH.unshift(File.join(__dir__, "vendor", "prettier_print-1.2.1", "lib"))
$LOAD_PATH.unshift(File.join(__dir__, "vendor", "syntax_tree-6.3.0", "lib"))
require "rubygems/version" # Only the stdlib version class, not RubyGems activation.
require "syntax_tree"

STDIN.set_encoding(Encoding::UTF_8)
STDOUT.set_encoding(Encoding::UTF_8)
source = STDIN.read
width = Integer(ARGV.fetch(0, "120"))

begin
  result = SyntaxTree.format(source, width)
  abort "The formatted result could not be parsed; the original file has been kept." unless Ripper.sexp(result)
  STDOUT.write(result)
rescue SyntaxTree::Parser::ParseError => error
  warn "Ruby syntax error at line #{error.lineno}, column #{error.column + 1}: #{error.message}"
  exit 2
end
