package testdata

import com.github.ghik.silencer.silent

object classSuppression {
  @silent
  class suppressed {
    def method(): Unit = {
      123
    }
  }
  class notSuppressed {
    def method(): Unit = {
      123
    }
  }

}
