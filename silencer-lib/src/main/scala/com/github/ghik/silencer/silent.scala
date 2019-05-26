package com.github.ghik.silencer

import scala.annotation.Annotation

/**
  * When silencer compiler plugin is enabled, this annotation suppresses warnings emitted by scalac for some portion
  * of source code. It can be applied on any definition (`class`, def`, `val`, `var`, etc.) or on arbitrary expression,
  * e.g. {123; 456}: @silent`.
  * You may also suppress specific classes of warnings by passing an optional warning message pattern argument to this
  * annotation. Only warnings matching the pattern will be suppressed.
  */
class silent extends Annotation {
  def this(messagePattern: String) = this()
}
