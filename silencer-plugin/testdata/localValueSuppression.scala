import rjghik.silencer.silent

object localValueSuppression {
  def method(): Unit = {
    @silent
    val stuff = {
      123
      543
    }
  }
}
