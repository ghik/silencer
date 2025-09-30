name := "silencer"

val saveTestClasspath = taskKey[File](
  "Saves test classpath to a file so that it can be used by embedded scalac in tests")

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

inThisBuild(Seq(
  organization := "com.github.ghik",
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq(
    "2.13.17", "2.13.16", "2.13.15", "2.13.14", "2.13.13", "2.13.12", "2.13.11", "2.13.10", "2.13.9", "2.13.8", "2.13.7", "2.13.6", "2.13.5", "2.13.4", "2.13.3", "2.13.2",
    "2.12.20", "2.12.19", "2.12.18", "2.12.17", "2.12.16", "2.12.15", "2.12.14", "2.12.13", "2.11.12"
  ),

  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
  githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),

  githubWorkflowPublish := Seq(WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )),
))

val commonSettings = Def.settings(
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
  ),

  crossVersion := CrossVersion.full,
  inConfig(Compile)(crossSources),
  inConfig(Test)(crossSources),
)

val subprojectSettings = Def.settings(
  commonSettings,
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
)

lazy val silencer = (project in file(".")).aggregate(`silencer-lib`, `silencer-plugin`)
  .settings(commonSettings*)
  .settings(
    publishArtifact := false,
    PgpKeys.publishSigned := {}
  )

lazy val `silencer-lib` = project
  .settings(subprojectSettings*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Test,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.1" % Test
    )
  )

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`)
  .settings(subprojectSettings*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scalatest" %% "scalatest-funsuite" % "3.2.0" % Test
    ),
    Test / resourceGenerators += Def.task {
      val result = (Test / resourceManaged).value / "embeddedcp"
      IO.write(result, (`silencer-lib` / Test / fullClasspath).value.map(_.data.getAbsolutePath).mkString("\n"))
      Seq(result)
    }.taskValue,
    Test / fork := true,
    Test / baseDirectory := (ThisBuild / baseDirectory).value,
  )
