package testdata

import rjghik.silencer.silent

object statementSuppression {
  def method(): Unit = {
    123: @silent
  }
}
