## Scala compiler plugin for annotation-based warning suppression

[![Build Status](https://travis-ci.org/ghik/silencer.svg?branch=master)](https://travis-ci.org/ghik/silencer)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin_2.12)

Scala has no local warning suppression (see e.g. [SI-1781](https://issues.scala-lang.org/browse/SI-1781) for discussion). This plugin aims to change the situation. The direct motivation for this plugin is to be able to turn on `-Xfatal-warnings` option in Scala compiler and enforce zero-warning policy but still be able to consciously silent out warnings which would otherwise be a pointless noise.

### Usage

If you're using SBT, simply add these lines to your `build.sbt` to enable the plugin:

```scala
val silencerVersion = "0.6"

libraryDependencies ++= Seq(
  compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
  "com.github.ghik" %% "silencer-lib" % silencerVersion
)
```
    
Silencer works with Scala 2.11.4+ and 2.12.0+. Also note that since both `silencer-plugin` and `silencer-lib` are compile time only dependencies, Silencer can also be used in ScalaJS without having to be cross compiled for it.

With the plugin enabled, warnings can be silenced using the `@com.github.ghik.silencer.silent` annotation. It can be applied on a single statement or expression, entire `def`/`val`/`var` definition or entire `class`/`object`/`trait` definition.

```scala
import com.github.ghik.silencer.silent

@silent class someClass { ... }
@silent def someMethod() = { ... }
someDeprecatedApi("something"): @silent
```

The `@silent` annotation suppresses *all* warnings in some code fragment. There is currently no way to silent out only specific classes of warnings, like with `@SuppressWarnings` annotation in Java.

### Status

Silencer is succesfully being used in [AVSystem](https://github.com/AVSystem) open source and closed projects, e.g. [AVSystem  Scala Commons Library](https://github.com/AVSystem/scala-commons)

