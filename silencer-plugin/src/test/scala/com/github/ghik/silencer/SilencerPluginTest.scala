package com.github.ghik.silencer

import java.io.File

import org.scalatest.FunSuite

import scala.io.Source
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}
import scala.util.Properties

abstract class SilencerPluginTest(options: String*) extends FunSuite { suite =>

  val testdata = "silencer-plugin/testdata/"
  val settings = new Settings

  settings.deprecation.value = true
  settings.warnUnused.enable(settings.UnusedWarnings.Imports)
  settings.pluginOptions.value = settings.pluginOptions.value ++ options.map(o => s"silencer:$o")

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
    reporter.reset()
    val run = new global.Run
    run.compile(filenames.toList.map(testdata + _))
  }

  def assertWarnings(count: Int): Unit =
    assert(count === reporter.warningCount)

  def assertErrors(count: Int): Unit =
    assert(count === reporter.errorCount)

  def testFile(filename: String, expectedWarnings: Int = 0, expectedErrors: Int = 0): Unit = {
    compile(filename)
    assertErrors(expectedErrors)
    assertWarnings(expectedWarnings)
  }

  // looks like macro args are not linted at all in 2.13
  val macroExpandeeWarnings: Int =
    if (Properties.versionString.contains("2.13")) 0 else 1
}

class AnnotationSuppressionTest extends SilencerPluginTest {
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
  test("annotation argument suppression") {
    testFile("annotArgSuppression.scala", 1)
  }
  test("late warning") {
    testFile("lateWarning.scala", 1)
  }
  test("macro expandee") {
    testFile("macroExpandeeSuppression.scala", macroExpandeeWarnings)
  }
  test("message patterns") {
    testFile("messagePatterns.scala", 1)
  }
  test("@nowarn support") {
    testFile("nowarnSupport.scala", 2)
  }

  test("multiple files compilation") {
    compile("unsuppressed.scala", "statementSuppression.scala", "localValueSuppression.scala")
    assertWarnings(3)
  }
}

class GlobalSuppressionTest extends SilencerPluginTest(
  "globalFilters=depreFunc1\\ in\\ object\\ globallyFiltered\\ is\\ deprecated;useless.*filter",
  "pathFilters=.*ByPath;inner/unfiltered.scala",
  "sourceRoots=silencer-plugin/testdata/inner"
) {
  test("global filters") {
    testFile("globallyFiltered.scala", 1)
  }
  test("global path filters") {
    testFile(s"inner${File.separator}globallyFilteredByPath.scala")
    testFile(s"inner${File.separator}unfiltered.scala", 2)
  }
}

class LineContentFiltersTest extends SilencerPluginTest(
  "lineContentFilters=^import java\\.util\\."
) {
  test("line content filters") {
    testFile("lineContentFiltered.scala", 1)
  }
}

class UnusedSuppressionTest extends SilencerPluginTest("checkUnused") {
  test("unused suppressions") {
    testFile("unusedSuppressions.scala", expectedErrors = 2)
  }
}
