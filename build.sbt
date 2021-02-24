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
  crossScalaVersions := Seq("2.13.5", "2.13.4", "2.13.3", "2.13.2", "2.12.13", "2.11.12"),

  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowJavaVersions := Seq("adopt@1.11"),
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
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.1" % Test
    )
  )

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`)
  .settings(subprojectSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scalatest" %% "scalatest-funsuite" % "3.2.0" % Test
    ),
    resourceGenerators in Test += Def.task {
      val result = (resourceManaged in Test).value / "embeddedcp"
      IO.write(result, (fullClasspath in `silencer-lib` in Test).value.map(_.data.getAbsolutePath).mkString("\n"))
      Seq(result)
    }.taskValue,
    fork in Test := true,
    baseDirectory in Test := (baseDirectory in ThisBuild).value,
  )
