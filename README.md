## Scala compiler plugin for annotation-based warning suppression

Scala has no way of warning suppression, which actually more hurts than helps you if you want to have some reasonable policy on warnings in your Scala codebase. See e.g. [this ticket](https://issues.scala-lang.org/browse/SI-1781) for more discussion. The immediate goal of author of this plugin is to be able to turn on `-Xfatal-warnings` option in Scala compiler and enforce zero-warning policy but still be able to consciously silent out warnings in specific code portions which would otherwise be a noise.

### Usage

With the plugin, warnings can be silenced using the `@com.github.ghik.silencer.silent` annotation. It can be applied on a single statement or expression, entire `def`/`val`/`var` definition or entire `class`/`object`/`trait` definition.

    import com.github.ghik.silencer.silent

    @silent class someClass { ... }
    @silent def someMethod() = { ... }
    someDeprecatedApi("something"): @silent

The `@silent` annotation suppresses *all* warnings in some code fragment. There is currently no way to silent out only specific classes of warnings, like with `@SuppressWarnings` annotation in Java.

### Status

Proof of concept.

