package com.github.ghik.silencer

// for name based extractors
final class OptRef[+T >: Null] private(private val value: T) extends AnyVal {
  def isEmpty: Boolean = value == null
  def get: T = value
}
object OptRef {
  def apply[T >: Null](value: T): OptRef[T] = new OptRef(value)
  def empty: OptRef[Null] = new OptRef(null)
}
