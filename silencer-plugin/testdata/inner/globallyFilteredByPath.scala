package testdata

// global file filter ignore all in file
object globallyFilteredByPath {
  class Suppressed {
    def method1(): Unit = deprecatedFileFunc1()

    def method2(): Unit = deprecatedFileFunc2()
  }

  @deprecated("", "")
  private def deprecatedFileFunc1() = print("fileIgnored")

  @deprecated("", "")
  private def deprecatedFileFunc2() = print("fileIgnored")
}
