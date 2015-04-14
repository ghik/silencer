package testdata

import com.github.ghik.silencer.silent

object statementSuppression {
  def method(): Unit = {
    123: @silent
    123
  }
}
