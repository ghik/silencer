# Silencer: Scala compiler plugin for warning suppression

[![Build Status](https://travis-ci.org/ghik/silencer.svg?branch=master)](https://travis-ci.org/ghik/silencer)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin_2.13.2/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin_2.13.2)

**NOTE**: Scala 2.13.2 introduced [configurable warnings](https://github.com/scala/scala/pull/8373).
This means that if you're using Scala 2.13 only, this plugin is obsolete and you should use
[`@nowarn`](https://www.scala-lang.org/api/current/scala/annotation/nowarn.html).

If you're using Scala 2.11/2.12 or cross-compiling for them then this plugin can be used in conjunction with
[scala-collection-compat](https://github.com/scala/scala-collection-compat) in order to suppress warnings in all 
Scala versions using `@nowarn`.

## Setup

If you're using SBT, add this to your project definition:

```scala
libraryDependencies ++= Seq(
  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
)
```

If you're using Gradle:

```groovy
ext {
    scalaVersion = "..." // e.g. "2.13.0"
    silencerVersion = "..." // appropriate silencer version
}
configurations {
    scalacPlugin {
        transitive = false
    }
}
dependencies {
    compile "com.github.ghik:silencer-lib_$scalaVersion:$silencerVersion"
    scalacPlugin "com.github.ghik:silencer-plugin_$scalaVersion:$silencerVersion"
}
tasks.withType(ScalaCompile) {
    scalaCompileOptions.additionalParameters =
            configurations.scalacPlugin.collect { "-Xplugin:" + it.absolutePath }
}
```
    
Silencer currently works with Scala 2.12.0+ and 2.13.0-M4+. Also note that since both `silencer-plugin` and 
`silencer-lib` are compile time only dependencies, Silencer can also be used in ScalaJS and Scala Native without having 
to be cross compiled for them.

## Annotation-based suppression

With the plugin enabled, warnings can be suppressed using the `@com.github.ghik.silencer.silent` 
or `@scala.annotation.nowarn` annotation. 
It can be applied on a single statement or expression, entire `def`/`val`/`var` definition or entire 
`class`/`object`/`trait` definition.

```scala
import com.github.ghik.silencer.silent

@silent class someClass { ... }
@silent def someMethod() = { ... }
someDeprecatedApi("something"): @silent
```

### Message pattern

By default the `@silent` annotation suppresses *all* warnings in some code fragment. You can limit the suppression to
some specific classes of warnings by passing a message pattern (regular expression) to the annotation, e.g.

```scala
@silent("deprecated") 
def usesDeprecatedApi(): Unit = {
  someDeprecatedApi("something")
}
```

### Using `@nowarn`

Scala 2.13.2 introduced [configurable warnings](https://github.com/scala/scala/pull/8373) using `-Wconf` compiler option 
and `@scala.annotation.nowarn`. annotation. For Scala 2.11 and 2.12, this annotation is provided by the 
[scala-collection-compat](https://github.com/scala/scala-collection-compat) library and interpreted by the `silencer`
plugin.

**NOTE**: `@nowarn` in Scala 2.13.2 supports various fine-grained filters (e.g. warning category, message patttern, etc.).
Silencer only supports the `msg=<pattern>` filter - all other filters simply suppress everything, as if there were
no filters specified.

### Detecting unused annotations

If a `@silent` annotation does not actually suppress any warnings, you can make `silencer` report an error in such
situation. This can be enabled by passing the `checkUnused` option to the plugin:

```scala
scalacOptions += "-P:silencer:checkUnused"
```

## Global regex-based suppression

You can also suppress warnings globally based on a warning message regex. In order to do that, pass this option to `scalac`:

```scala
scalacOptions += "-P:silencer:globalFilters=<semicolon separated message regexes>"
```

## Line content based suppression

Filtering may also be based on the content of source line that generated the warning.
This is particularly useful for suppressing 'unused import' warnings based on what's being imported.

```scala
scalacOptions += "-P:silencer:lineContentFilters=<semicolon separated line content regexes>"
```

## Filename based suppression

Another option is to suppress all warnings in selected source files. This can be done by specifying a list of file path regexes:

```scala
scalacOptions += "-P:silencer:pathFilters=<semicolon separated file path regexes>"
```

**NOTE**: In order to make builds independent of environment, filename separators are normalized to UNIX style (`/`) 
before the path is matched against path patterns.

By default, absolute file path is matched against path patterns. In order to make your build independent of where your 
project is checked out, you can specify a list of source root directories. Source file paths will be relativized with 
respect to them  before being matched against path patterns. Usually it should be enough to pass project base directory 
as source root (i.e. `baseDirectory.value` in SBT):

```scala
scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}"
```

Another good choice for source roots may be actual SBT source directories:

```scala
scalacOptions += s"-P:silencer:sourceRoots=${sourceDirectories.value.map(_.getCanonicalPath).mkString(";")}"
```

## Searching macro expansions

By default (starting from version 1.6.0) silencer does not look for `@silent` annotations in macro expansions.
If you want to bring back the old behaviour where both macro expansions and expandees are searched, use the
`-P:silencer:searchMacroExpansions` option.
