
name := "silencer"

val saveTestClasspath = taskKey[File](
  "Saves test classpath to a file so that it can be used by embedded scalac in tests")

useGpg := false // TODO: use sbt-ci-release
pgpPublicRing := file("./travis/local.pubring.asc")
pgpSecretRing := file("./travis/local.secring.asc")
pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray)

credentials in Global += Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  sys.env.getOrElse("SONATYPE_USERNAME", ""),
  sys.env.getOrElse("SONATYPE_PASSWORD", "")
)

version in ThisBuild :=
  sys.env.get("TRAVIS_TAG").filter(_.startsWith("v")).map(_.drop(1)).getOrElse("1.5-SNAPSHOT")

def crossSources = Def.settings(
  unmanagedSourceDirectories ++= unmanagedSourceDirectories.value.flatMap { dir =>
    val path = dir.getPath
    val sv = scalaVersion.value
    val suffixes = CrossVersion.partialVersion(sv) match {
      case Some((2, 11)) => Seq("2.11", "2.11-12")
      case Some((2, 12)) => Seq("2.11-12", "2.12", "2.12-13")
      case Some((2, 13)) => Seq("2.12-13", "2.13")
      case _ => throw new IllegalArgumentException("unsupported scala version")
    }
    suffixes.map(s => file(s"$path-$s"))
  }
)

val commonSettings = Def.settings(
  organization := "com.github.ghik",
  scalaVersion := crossScalaVersions.value.head,
  crossVersion := CrossVersion.full,
  crossScalaVersions := Seq("2.13.2", "2.13.1", "2.12.11", "2.11.12"),
  inConfig(Compile)(crossSources),
  inConfig(Test)(crossSources),
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

val subprojectSettings = Def.settings(
  commonSettings,
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  publishTo := sonatypePublishToBundle.value,
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
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Test,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6" % Test
    )
  )

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`)
  .settings(subprojectSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    ),
    resourceGenerators in Test += Def.task {
      val result = (resourceManaged in Test).value / "embeddedcp"
      IO.write(result, (fullClasspath in `silencer-lib` in Test).value.map(_.data.getAbsolutePath).mkString("\n"))
      Seq(result)
    }.taskValue,
    fork in Test := true,
    baseDirectory in Test := (baseDirectory in ThisBuild).value,
  )
