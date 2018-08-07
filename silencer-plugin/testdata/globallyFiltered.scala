package testdata

object globallyFiltered {
  class notSuppressed {
    def method(): Unit = depreFunc()
  }

  @deprecated("", "")
  private def depreFunc() = print("ignored")
}
