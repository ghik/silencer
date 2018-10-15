
name := "silencer"

val saveTestClasspath = taskKey[File](
  "Saves test classpath to a file so that it can be used by embedded scalac in tests")

val commonSettings = Seq(
  organization := "com.github.ghik",
  version := "1.2.1",
  scalaVersion := "2.12.7",
  crossScalaVersions := Seq("2.11.12", scalaVersion.value, "2.13.0-M4"),
  projectInfo := ModuleInfo(
    nameFormal = "Silencer",
    description = "Scala compiler plugin for annotation-based warning suppression",
    homepage = Some(url("https://github.com/ghik/silencer")),
    startYear = Some(2015),
    licenses = Vector(
      "The Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
    ),
    organizationName = "ghik",
    organizationHomepage = Some(url("https://github.com/ghik")),
    scmInfo = Some(ScmInfo(
      browseUrl = url("https://github.com/ghik/silencer.git"),
      connection = "scm:git:git@github.com:ghik/silencer.git",
      devConnection = Some("scm:git:git@github.com:ghik/silencer.git")
    )),
    developers = Vector(
      Developer("ghik", "Roman Janusz", "romeqjanoosh@gmail.com", url("https://github.com/ghik"))
    ),
  )
)

val subprojectSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  pomIncludeRepository := { _ => false },
)

lazy val silencer = (project in file(".")).aggregate(`silencer-lib`, `silencer-plugin`)
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    PgpKeys.publishSigned := {}
  )

lazy val `silencer-lib` = project
  .settings(subprojectSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Test
    )
  )

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`)
  .settings(subprojectSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scalatest" %% "scalatest" % "3.0.6-SNAP1" % Test
    ),
    saveTestClasspath := {
      val result = (classDirectory in Test).value / "embeddedcp"
      IO.write(result, (fullClasspath in `silencer-lib` in Test).value.map(_.data.getAbsolutePath).mkString("\n"))
      result
    },
    (test in Test) := (test in Test).dependsOn(saveTestClasspath).value,
    fork in Test := true,
    baseDirectory in Test := (baseDirectory in ThisBuild).value,
  )
