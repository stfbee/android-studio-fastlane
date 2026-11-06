# frozen_string_literal: true

# Keep user-authored multiline literal spelling intact. In particular Ruby parses
# """...""" as a StringConcat, whose normal formatter introduces line continuations.
# Prepending only the node formatter leaves Syntax Tree's comment handling intact.
module FastlanePreserveMultilineStrings
  def format(q)
    original = q.source[start_char...end_char]
    return super unless original.include?("\n")

    original.split("\n", -1).each_with_index do |line, index|
      q.breakable_return unless index.zero?
      q.text(line)
    end
  end
end

SyntaxTree::StringLiteral.prepend(FastlanePreserveMultilineStrings)
SyntaxTree::StringConcat.prepend(FastlanePreserveMultilineStrings)
SyntaxTree::XStringLiteral.prepend(FastlanePreserveMultilineStrings)
