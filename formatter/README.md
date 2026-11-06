# Bundled Ruby formatter

`format.rb` uses Syntax Tree 6.3.0 and prettier_print 1.2.1, downloaded as versioned gems from RubyGems. Only their `lib` directories and MIT licenses are included; upstream files are unmodified.

- https://github.com/ruby-syntax-tree/syntax_tree
- https://github.com/ruby-syntax-tree/prettier_print

The IDE passes unsaved document text through stdin and applies stdout as one formatting operation. No source file or project configuration is loaded as Ruby code. The wrapper does not execute the user's Fastfile, load Bundler, install gems, or read project formatting plugins. It requires Ruby 2.7+ with the standard Ripper parser.

Formatting follows Syntax Tree's style (two spaces per indentation level), with the line width supplied by the IDE. Syntax errors return a nonzero exit status and leave the editor document unchanged.

Original gem SHA-256 checksums:

```text
56e25a9692c798ec94c5442fe94c5e94af76bef91edc8bb02052cbdecf35f13d  syntax_tree-6.3.0.gem
a72838b5f23facff21f90a5423cdcdda19e4271092b41f4ea7f50b83929e6ff9  prettier_print-1.2.1.gem
```
