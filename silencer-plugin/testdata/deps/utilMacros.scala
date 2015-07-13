import scala.reflect.macros.blackbox

class utilMacros(val c: blackbox.Context) {
  import c.universe._
  
  def discard(expr: c.Expr[Any]): c.Tree =
    q"()"
}
