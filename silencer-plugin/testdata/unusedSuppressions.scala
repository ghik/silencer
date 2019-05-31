package testdata

import com.github.ghik.silencer.silent

object unusedSuppressions {
  @deprecated("", "") def defaultArg: Int = 42
  class A(i: Int = defaultArg: @silent)

  @silent def nothingToSuppress(): Unit = ()

  @silent def enoughSuppression(): Unit = {
    123
  }

  @silent def tooMuchSuppression(): Unit = {
    123: @silent
  }
}
