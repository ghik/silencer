## Scala compiler plugin for warning suppression

[![Build Status](https://travis-ci.org/ghik/silencer.svg?branch=master)](https://travis-ci.org/ghik/silencer)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.ghik/silencer-plugin_2.12)

Scala has no local warning suppression (see e.g. [scala/bug/issues/1781](https://github.com/scala/bug/issues/1781) for discussion). This plugin aims to change the situation. The direct motivation for this plugin is to be able to turn on `-Xfatal-warnings` option in Scala compiler and enforce zero-warning policy but still be able to consciously silent out warnings which would otherwise be a pointless noise.

### Usage

If you're using SBT, simply add these lines to your `build.sbt` to enable the plugin:

```scala
libraryDependencies ++= Seq(
  compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
  "com.github.ghik" %% "silencer-lib" % silencerVersion % Provided
)
```

If you're using Gradle:

```groovy
ext {
    scalaBinaryVersion = "..." // e.g. "2.12"
    silencerVersion = "..." // appropriate silencer version
}
configurations {
    scalacPlugin {
        transitive = false
    }
}
dependencies {
    compile "com.github.ghik:silencer-lib_$scalaBinaryVersion:$silencerVersion"
    scalacPlugin "com.github.ghik:silencer-plugin_$scalaBinaryVersion:$silencerVersion"
}
tasks.withType(ScalaCompile) {
    scalaCompileOptions.additionalParameters =
            configurations.scalacPlugin.collect { "-Xplugin:" + it.absolutePath }
}
```
    
Silencer currently works with Scala 2.11.4+, 2.12.0+ and 2.13.0-M4+. Also note that since both `silencer-plugin` and `silencer-lib` are compile time only dependencies, Silencer can also be used in ScalaJS and Scala Native without having to be cross compiled for it.

#### Annotation-based suppression

With the plugin enabled, warnings can be silenced using the `@com.github.ghik.silencer.silent` annotation. It can be applied on a single statement or expression, entire `def`/`val`/`var` definition or entire `class`/`object`/`trait` definition.

```scala
import com.github.ghik.silencer.silent

@silent class someClass { ... }
@silent def someMethod() = { ... }
someDeprecatedApi("something"): @silent
```

The `@silent` annotation suppresses *all* warnings in some code fragment. There is currently no way to silent out only specific classes of warnings, like with `@SuppressWarnings` annotation in Java.

#### Global regex-based suppression

You can also suppress warnings globally based on a warning message regex. In order to do that, pass this option to `scalac`:

```scala
scalacOptions += "-P:silencer:globalFilters=[semi-colon separated message patterns]"
```

#### Filename based suppression

Another option is to suppress all warnings in selected source files. This can be done by specifying a list of file path regexes:

```scala
scalacOptions += "-P:silencer:pathFilters=[semi-colon separated file path patterns]"
```

**NOTE**: In order to make builds independent of environment, filename separators are normalized to UNIX style (`/`) before the path is matched against path patterns.

By default, absolute file path is matched against path patterns. In order to make your build independent of where your project is checked out, you can specify a list of source root directories. Source file paths will be relativized with respect to them  before being matched against path patterns. Usually it should be enough to pass project base directory as source root (i.e. `baseDirectory.value` in SBT):

```scala
scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}"
```

Another good choice for source roots may be actual SBT source directories:

```scala
scalacOptions += s"-P:silencer:sourceRoots=${sourceDirectories.value.map(_.getCanonicalPath).mkString(";")}"
```

### Status

Silencer is successfully being used in [AVSystem](https://github.com/AVSystem) open source and closed projects, e.g. [AVSystem  Scala Commons Library](https://github.com/AVSystem/scala-commons)

