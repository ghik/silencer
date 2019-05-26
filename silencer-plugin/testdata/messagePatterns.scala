package testdata

import com.github.ghik.silencer.silent

object messagePatterns {
  @silent("pure expression")
  def method(): Unit = {
    123
  }
  @silent("something else")
  def other(): Unit = {
    123
  }
}
