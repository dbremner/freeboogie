# Overview #

  * Unless otherwise specified below, follow the [Sun conventions](http://java.sun.com/docs/codeconv/).
  * Indent by 2 spaces. Do _not_ use tabs. Indent continuation lines by 4 spaces.
  * A property `foo` of type `T` is implemented by a pair of methods `public T foo()` and `public void foo(T value)`. The prefix get/set is _not_ used.
  * The import section is made out of three groups separated by an empty line: (1) imports from the Java API, (2) imports from libraries, (3) imports from other packages of the same project. In each group, static imports come _after_ normal ones, and otherwise the order is lexicographic. A `*` import should be used iff (1) there are more than three names that need to be imported and (2) there is no naming ambiguity introduced by using the star.
  * Do _not_ use curly brackets for one-statement-one-line blocks. If more than one line is taken by the block then do use them. (Obviously, you _have_ to use them if more than one statement is in the block.) Also, if they are a loop body or follow an `if/else`, then put them on the same line as `if/for/while/...` if possible. Remember the Sun limitation to 80 character lines per line.
  * Do _not_ use parentheses, unless needed.
  * If you have to split a list on multiple lines (such as a parameter list) then put one item per line.
  * The length of variable names should be (positively) correlated to the size of their scope. A non-private member **must** be named using whole words. A private member **may** be shorter if the class is very small (less than 20 lines). A local variable **may** be one-letter long, and probably should be one letter long if it is used in a scope of less than 5 lines.

# Discussion #

No discussion yet. Use comments if you disagree and I'll either explain here or change my mind.