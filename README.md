## Scala compiler plugin for annotation-based warning suppression

[![Build Status](https://travis-ci.org/ghik/silencer.svg?branch=master)](https://travis-ci.org/ghik/silencer)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin)

In Scala, it is generally not possible to suppress warnings locally. This actually hurts more than helps you if you want to have some reasonable policy on warnings in your Scala codebase. See e.g. [this ticket](https://issues.scala-lang.org/browse/SI-1781) for more discussion. This plugin aims to change the situation. The direct motivation for this plugin is to be able to turn on `-Xfatal-warnings` option in Scala compiler and enforce zero-warning policy but still be able to consciously silent out warnings in specific code portions which would otherwise be a noise.

### Usage

If you're using SBT, simply add these lines to your `build.sbt` to enable the plugin:

    addCompilerPlugin("com.github.ghik" % "silencer-plugin" % "0.3")
    
    libraryDependencies += "com.github.ghik" % "silencer-lib" % "0.3"
    
Scala 2.11.4 or newer is required.

With the plugin enabled, warnings can be silenced using the `@com.github.ghik.silencer.silent` annotation. It can be applied on a single statement or expression, entire `def`/`val`/`var` definition or entire `class`/`object`/`trait` definition.

    import com.github.ghik.silencer.silent

    @silent class someClass { ... }
    @silent def someMethod() = { ... }
    someDeprecatedApi("something"): @silent

The `@silent` annotation suppresses *all* warnings in some code fragment. There is currently no way to silent out only specific classes of warnings, like with `@SuppressWarnings` annotation in Java.

### Status

Proof of concept and more or less a hack on the compiler.

