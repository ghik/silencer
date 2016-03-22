## Scala compiler plugin for annotation-based warning suppression

[![Build Status](https://travis-ci.org/ghik/silencer.svg?branch=master)](https://travis-ci.org/ghik/silencer)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin)

Scala has no local warning suppression (see e.g. [SI-1781](https://issues.scala-lang.org/browse/SI-1781) for discussion). This plugin aims to change the situation. The direct motivation for this plugin is to be able to turn on `-Xfatal-warnings` option in Scala compiler and enforce zero-warning policy but still be able to consciously silent out warnings which would otherwise be a pointless noise.

### Usage

If you're using SBT, simply add these lines to your `build.sbt` to enable the plugin:

    addCompilerPlugin("com.github.ghik" % "silencer-plugin" % "0.4")
    
    libraryDependencies += "com.github.ghik" % "silencer-lib" % "0.4"
    
Scala 2.11.4 or newer is required. Not yet tested on 2.12.x

With the plugin enabled, warnings can be silenced using the `@com.github.ghik.silencer.silent` annotation. It can be applied on a single statement or expression, entire `def`/`val`/`var` definition or entire `class`/`object`/`trait` definition.

    import com.github.ghik.silencer.silent

    @silent class someClass { ... }
    @silent def someMethod() = { ... }
    someDeprecatedApi("something"): @silent

The `@silent` annotation suppresses *all* warnings in some code fragment. There is currently no way to silent out only specific classes of warnings, like with `@SuppressWarnings` annotation in Java.

### Status

Originally developed as a proof of concept and more or less a hack on the compiler, but it's being used in [AVSystem](https://github.com/AVSystem) projects and has not caused any problems so far.

