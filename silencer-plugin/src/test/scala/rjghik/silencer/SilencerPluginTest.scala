package rjghik.silencer

import java.io.File

import org.scalatest.FunSuite

import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

class SilencerPluginTest extends FunSuite {
  suite =>

  val settings = new Settings
  settings.usejavacp.value = true
  // avoid saving classfiles to disk
  settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))
  val reporter = new ConsoleReporter(settings)

  val global = new Global(settings, reporter) {
    override protected def loadRoughPluginsList() =
      new SilencerPlugin(this) :: super.loadRoughPluginsList()
  }

  def compile(filenames: String*): Unit = {
    val run = new global.Run
    run.compile(filenames.toList.map("testdata/" + _))
  }

  def assertWarnings(count: Int): Unit = {
    assert(!reporter.hasErrors)
    assert(count === reporter.warningCount)
  }

  test("no suppression") {
    compile("unsuppressed.scala")
    assertWarnings(1)
  }

  test("statement suppression") {
    compile("statementSuppression.scala")
    assertWarnings(0)
  }

  test("local value suppression") {
    compile("localValueSuppression.scala")
    assertWarnings(0)
  }

  test("method suppression") {
    compile("methodSuppression.scala")
    assertWarnings(0)
  }

  test("class suppression") {
    compile("classSuppression.scala")
    assertWarnings(0)
  }

  test("multiple files compilation") {
    compile(new File("testdata").listFiles().map(_.getName): _*)
    assertWarnings(1)
  }

}
