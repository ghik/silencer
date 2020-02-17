package com.github.ghik.silencer

import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.util.{Position, SourceFile}
import scala.reflect.io.AbstractFile
import scala.tools.nsc.reporters.{FilteringReporter, ForwardingReporter}
import scala.util.matching.Regex

class SuppressingReporter(
  original: FilteringReporter,
  globalFilters: List[Regex],
  protected val pathFilters: List[Regex],
  protected val sourceRoots: List[AbstractFile]
) extends ForwardingReporter(original) with SuppressingReporterBase {
  //Suppressions are sorted by end offset of their suppression ranges so that nested suppressions come before
  //their containing suppressions. This is ensured by FindSuppressions traverser in SilencerPlugin.
  //This order is important for proper unused @silent annotation detection.
  def isSuppressed(suppressions: List[Suppression], pos: Position, msg: String): Boolean =
    suppressions.find(_.suppresses(pos, msg)) match {
      case Some(suppression) =>
        suppression.used = true
        true
      case _ =>
        false
    }

  def setSuppressions(source: SourceFile, suppressions: List[Suppression]): Unit = {
    fileSuppressions(source) = suppressions
    for ((pos, msg) <- deferredWarnings.remove(source).getOrElse(Seq.empty))
      warning(pos, msg) // will invoke `filter`
  }

  override def reset(): Unit = {
    super.reset()
    deferredWarnings.clear()
    fileSuppressions.clear()
  }

  /** Return
   *   - 0: count and display
   *   - 1: count only, don't display
   *   - 2: don't count, don't display
   */
  override def filter(pos: Position, msg: String, severity: Severity): Int = {
    def globallySuppressed: Boolean =
      matchesPathFilter(pos) || anyMatches(globalFilters, msg)

    def locallySuppressed: Boolean = fileSuppressions.get(pos.source) match {
      case Some(suppressions) => isSuppressed(suppressions, pos, msg)
      case None =>
        deferredWarnings.getOrElseUpdate(pos.source, new ArrayBuffer) += ((pos, msg))
        true
    }

    if (severity == WARNING && (globallySuppressed || locallySuppressed)) 2
    else super.filter(pos, msg, severity)
  }
}
