package testdata

object unsuppressed {
  @deprecated("don't", "0.0") def depreMethod(): Unit = ()

  def method(): Unit = {
    depreMethod()
  }
}
