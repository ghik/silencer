package testdata

import com.github.ghik.silencer.silent

import scala.language.experimental.macros

object macroExpandeeSuppression {
  def discard(expr: Any): Unit = macro com.github.ghik.silencer.MacroImpls.discard

  discard {
    123: Unit
    456: @silent
  }

  discard {
    123
  }: @silent
}
