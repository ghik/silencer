package com.github.ghik.silencer

import java.io.File

import org.scalatest.FunSuite

import scala.io.Source
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}
import scala.util.Properties

class SilencerPluginTest extends FunSuite { suite =>

  val testdata = "silencer-plugin/testdata/"
  val settings = new Settings

  settings.deprecation.value = true
  settings.pluginOptions.value = settings.pluginOptions.value :+
    "silencer:globalFilters=depreFunc1\\ in\\ object\\ globallyFiltered\\ is\\ deprecated;useless.*filter" :+
    "silencer:pathFilters=.*ByPath;inner/unfiltered.scala" :+
    "silencer:sourceRoots=silencer-plugin/testdata/inner"

  Option(getClass.getResourceAsStream("/embeddedcp")) match {
    case Some(is) =>
      Source.fromInputStream(is).getLines().foreach(settings.classpath.append)
    case None =>
      settings.usejavacp.value = true
  }

  // avoid saving classfiles to disk
  val outDir = new VirtualDirectory("(memory)", None)
  settings.outputDirs.setSingleOutput(outDir)
  val reporter = new ConsoleReporter(settings)

  val global: Global = new Global(settings, reporter) {
    override protected def loadRoughPluginsList(): List[Plugin] =
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

  // looks like macro args are not linted at all in 2.13
  val macroExpandeeWarnings: Int =
    if (Properties.versionString.contains("2.13")) 0 else 1

  test("unsuppressed") {
    testFile("unsuppressed.scala", 1)
  }
  test("statement suppression") {
    testFile("statementSuppression.scala", 1)
  }
  test("local value suppression") {
    testFile("localValueSuppression.scala", 1)
  }
  test("method suppression") {
    testFile("methodSuppression.scala", 1)
  }
  test("class suppression") {
    testFile("classSuppression.scala", 1)
  }
  test("late warning") {
    testFile("lateWarning.scala", 1)
  }
  test("macro expandee") {
    testFile("macroExpandeeSuppression.scala", macroExpandeeWarnings)
  }
  test("global filters") {
    testFile("globallyFiltered.scala", 1)
  }
  test("global path filters") {
    testFile(s"inner${File.separator}globallyFilteredByPath.scala")
    testFile(s"inner${File.separator}unfiltered.scala", 2)
  }
  test("multiple files compilation") {
    val files = new File(testdata).listFiles().filter(_.isFile).map(_.getName)
    compile(files: _*)
    assertWarnings(files.length - 1 + macroExpandeeWarnings - 1) // one is excluded by path
  }
}
