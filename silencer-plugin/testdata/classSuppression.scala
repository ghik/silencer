package testdata

import rjghik.silencer.silent

@silent
object classSuppression {
  def method(): Unit = {
    123
  }
}
