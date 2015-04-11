package testdata

import com.github.ghik.silencer.silent

@silent
object classSuppression {
  def method(): Unit = {
    123
  }
}
