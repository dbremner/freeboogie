# Introduction #

AstGen is a component used to generate various artifacts from a simple abstract grammar. For example, from the abstract grammar
```
Program =
  [list] Function functions;

Function =
  String! name,
  String comment,
  [list] String arguments,
  [list] Command commands;

Command :> Assignment, IO;

Assignment =
  String! lhs,
  Expression rhs;
```

it can generate a bunch of Java classes such as
```
public class Function extends Ast {
  private final Location location;
  private final String name;
  private final String comment;
  private final List<String> arguments;
  private final List<Command> commands;

  private Function(
      String name,
      String comment,
      List<String> arguments, 
      List<Command> commands
  ) {
    Preconditions.checkNotNull(name);
    Preconditions.checkNotNull(arguments);
    Preconditions.checkNotNull(commands);
    this.name = name;
    this.comment = comment;
    this.arguments = arguments;
    this.commands = commands;
  }

  public static Function mk(
}
```

from a template such as
```
TODO
```

and a graphviz diagram of the hierarchy
```
TODO
```

from a template such as
```
TODO
```

## Invocation ##

> ` [options] ABSTRACT_GRAMMAR TEMPLATES `

Where -b CLASSNAME determines the parent for classes with no parent in the grammar,   -d DIRECTORY determines the directory for files created according to the templates.