package com.github.ghik.silencer

import scala.reflect.api.Trees

abstract class SilencerNamedArgExtractor(trees: Trees) {
  import trees._

  def unapply(namedArg: Trees#Tree): Option[(Trees#Tree, Trees#Tree)] =
    AssignOrNamedArg.unapply(namedArg.asInstanceOf[AssignOrNamedArg])
}
