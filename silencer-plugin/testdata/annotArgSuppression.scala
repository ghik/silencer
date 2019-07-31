package testdata

import com.github.ghik.silencer.silent

object annotArgSuppression {
  @deprecated("", "") def depreMethod: String = ""

  class someAnnot(arg: String) extends scala.annotation.Annotation

  @someAnnot(depreMethod: @silent) object o1
  @someAnnot(depreMethod) object o2
}
