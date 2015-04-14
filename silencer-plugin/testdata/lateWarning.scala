import com.github.ghik.silencer.silent

object lateWarning {
  // main method in companion object of a trait will trigger a warning in compiler backend

  trait Suppressed
  @silent object Suppressed {
    def main(args: Array[String]) = ()
  }

  trait NotSuppressed
  object NotSuppressed {
    def main(args: Array[String]) = ()
  }
}
