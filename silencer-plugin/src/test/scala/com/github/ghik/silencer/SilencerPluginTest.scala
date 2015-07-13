package com.github.ghik.silencer

import java.io.File

import org.scalatest.FunSuite

import scala.io.Source
import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.typechecker.Analyzer
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
  val outDir = new VirtualDirectory("(memory)", None)
  settings.outputDirs.setSingleOutput(outDir)
  val reporter = new ConsoleReporter(settings)

  val global = new Global(settings, reporter) { g =>
    override protected def loadRoughPluginsList() =
      new SilencerPlugin(this) :: super.loadRoughPluginsList()

    override lazy val analyzer = new {
      val global: g.type = g
    } with Analyzer {
      override protected def findMacroClassLoader(): ClassLoader =
        new AbstractFileClassLoader(outDir, ClassLoader.getSystemClassLoader)
    }
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
    compile("deps/utilMacros.scala")
    testFile("macroExpandeeSuppression.scala", 1)
  }
  test("multiple files compilation") {
    val files = new File(testdata).listFiles().map(_.getName)
    compile(files: _*)
    assertWarnings(files.length)
  }

}
