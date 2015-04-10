(name in Global) := "silencer"

(organization in Global) := "rjghik"

(version in Global) := "0.1"

(scalaVersion in Global) := "2.11.6"

lazy val `silencer-lib` = project

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`).settings(
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  )
)
