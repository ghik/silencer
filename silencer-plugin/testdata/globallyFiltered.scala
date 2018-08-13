package testdata

object globallyFiltered {
  class notSuppressed {
    def method1(): Unit = depreFunc1() // global filter ignore this one

    def method2(): Unit = depreFunc2() // not suppressed, no filter
  }

  @deprecated("", "")
  private def depreFunc1() = print("ignored")

  @deprecated("", "")
  private def depreFunc2() = print("warning")
}
