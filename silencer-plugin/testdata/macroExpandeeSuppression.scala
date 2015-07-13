import com.github.ghik.silencer.silent

import scala.language.experimental.macros

object macroExpandeeSuppression {
  def discard(expr: Any): Unit = macro utilMacros.discard

  discard {
    123: Unit
    456: @silent
  }

  discard {
    123
  }: @silent
}
