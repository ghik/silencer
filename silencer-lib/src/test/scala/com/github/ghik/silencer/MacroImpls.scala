package com.github.ghik.silencer

import scala.reflect.macros.blackbox

class MacroImpls(val c: blackbox.Context) {
  import c.universe._

  def discard(expr: c.Expr[Any]): c.Tree =
    q"()"
}
