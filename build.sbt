(name in Global) := "silencer"

(organization in Global) := "com.github.ghik"

(version in Global) := "0.1"

(scalaVersion in Global) := "2.11.6"

val saveTestClasspath = taskKey[File](
  "Saves test classpath to a file so that it can be used by embedded scalac in tests")

lazy val `silencer-lib` = project

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`).settings(
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  ),
  saveTestClasspath := {
    val result = (classDirectory in Test).value / "embeddedcp"
    IO.write(result, (fullClasspath in Test).value.map(_.data.getAbsolutePath).mkString("\n"))
    result
  },
  (test in Test) <<= (test in Test) dependsOn saveTestClasspath
)
