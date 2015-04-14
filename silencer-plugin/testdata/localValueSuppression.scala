import com.github.ghik.silencer.silent

object localValueSuppression {
  def method(): Unit = {
    @silent
    val stuff = {
      123
      ()
    }
    val other = {
      123
      ()
    }
  }
}
