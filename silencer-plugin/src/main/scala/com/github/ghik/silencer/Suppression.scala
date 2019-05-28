package com.github.ghik.silencer

import scala.reflect.internal.util.Position
import scala.tools.nsc.reporters.Reporter
import scala.util.matching.Regex

class Suppression(annotPos: Position, range: Position, msgPattern: Option[Regex], inMacroExpansion: Boolean) {
  var used: Boolean = false

  def suppresses(pos: Position, msg: String): Boolean =
    range.includes(pos) && msgPattern.forall(_.findFirstIn(msg).isDefined)

  def reportUnused(reporter: Reporter): Unit =
    if (!inMacroExpansion && !used) {
      reporter.error(annotPos, s"this @silent annotation does not suppress any warnings")
    }
}
