package testdata

import com.github.ghik.silencer.silent

object statementSuppression {
  @deprecated("don't", "0.0") def depreMethod(): Unit = ()

  def method(): Unit = {
    depreMethod(): @silent
    depreMethod()
  }
}
