
name := "silencer"

val saveTestClasspath = taskKey[File](
  "Saves test classpath to a file so that it can be used by embedded scalac in tests")

val commonSettings = Seq(
  organization := "com.github.ghik",
  version := "0.5",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4"),
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
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
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

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`)
  .settings(subprojectSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scalatest" %% "scalatest" % "3.0.3" % Test
    ),
    saveTestClasspath := {
      val result = (classDirectory in Test).value / "embeddedcp"
      IO.write(result, (fullClasspath in Test).value.map(_.data.getAbsolutePath).mkString("\n"))
      result
    },
    (test in Test) := (test in Test).dependsOn(saveTestClasspath).value,
    fork in Test := true,
    baseDirectory in Test := (baseDirectory in ThisBuild).value,
  )
