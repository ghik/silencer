package testdata

import com.github.ghik.silencer.silent

object unusedSuppressions {
  @silent def nothingToSuppress(): Unit = ()

  @silent def enoughSuppression(): Unit = {
    123
  }

  @silent def tooMuchSuppression(): Unit = {
    123: @silent
  }
}
