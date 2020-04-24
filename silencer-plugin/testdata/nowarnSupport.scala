package testdata

import scala.annotation.nowarn

object nowarnSupport {
  @deprecated("don't", "0.0")
  def depreMethod(): String = ???

  depreMethod()
  depreMethod(): @nowarn("msg=unmatching")

  depreMethod(): @nowarn
  depreMethod(): @nowarn("msg=deprecated")
  depreMethod(): @nowarn("cat=deprecation")
}
