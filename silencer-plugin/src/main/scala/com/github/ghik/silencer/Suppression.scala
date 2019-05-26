package com.github.ghik.silencer

import scala.reflect.internal.util.Position
import scala.util.matching.Regex

class Suppression(range: Position, msgPattern: Option[Regex]) {
  def suppresses(pos: Position, msg: String): Boolean =
    range.includes(pos) && msgPattern.forall(_.findFirstIn(msg).isDefined)
}
