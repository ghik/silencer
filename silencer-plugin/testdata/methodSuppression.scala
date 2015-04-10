package testdata

import rjghik.silencer.silent

object methodSuppression {
  @silent
  def method(): Unit = {
    123
  }
}
