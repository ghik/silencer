package com.github.ghik.silencer

import java.io.File

import org.scalatest.FunSuite

import scala.io.Source
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

class SilencerPluginTest extends FunSuite {
  suite =>

  val testdata = "silencer-plugin/testdata/"
  val settings = new Settings

  Option(getClass.getResourceAsStream("/embeddedcp")) match {
    case Some(is) =>
      Source.fromInputStream(is).getLines().foreach(settings.classpath.append)
    case None =>
      settings.usejavacp.value = true
  }

  // avoid saving classfiles to disk
  settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))
  val reporter = new ConsoleReporter(settings)

  val global = new Global(settings, reporter) {
    override protected def loadRoughPluginsList() =
      new SilencerPlugin(this) :: super.loadRoughPluginsList()
  }

  def compile(filenames: String*): Unit = {
    val run = new global.Run
    run.compile(filenames.toList.map(testdata + _))
  }

  def assertWarnings(count: Int): Unit = {
    assert(!reporter.hasErrors)
    assert(count === reporter.warningCount)
  }

  def testFile(filename: String, expectedWarnings: Int = 0): Unit = {
    compile(filename)
    assertWarnings(expectedWarnings)
  }

  test("unsuppressed") {
    testFile("unsuppressed.scala", 1)
  }
  test("statement suppression") {
    testFile("statementSuppression.scala")
  }
  test("local value suppression") {
    testFile("localValueSuppression.scala")
  }
  test("method suppression") {
    testFile("methodSuppression.scala")
  }
  test("class suppression") {
    testFile("classSuppression.scala")
  }
  test("multiple files compilation") {
    compile(new File(testdata).listFiles().map(_.getName): _*)
    assertWarnings(1)
  }

}
