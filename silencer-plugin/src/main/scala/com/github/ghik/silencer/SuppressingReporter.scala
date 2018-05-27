package com.github.ghik.silencer

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.util.{Position, SourceFile}
import scala.tools.nsc.reporters.Reporter

class SuppressingReporter(original: Reporter) extends Reporter {
  private val deferredWarnings = new mutable.HashMap[SourceFile, ArrayBuffer[(Position, String)]]
  private val suppressedRanges = new mutable.HashMap[SourceFile, List[Position]]

  def setSuppressedRanges(source: SourceFile, ranges: List[Position]): Unit = {
    suppressedRanges(source) = ranges
    for ((pos, msg) <- deferredWarnings.remove(source).getOrElse(Seq.empty) if !ranges.exists(_.includes(pos))) {
      original.warning(pos, msg)
    }
    updateCounts()
  }

  override def reset(): Unit = {
    super.reset()
    original.reset()
    deferredWarnings.clear()
    suppressedRanges.clear()
  }

  protected def info0(pos: Position, msg: String, severity: Severity, force: Boolean): Unit = {
    severity match {
      case INFO =>
        original.info(pos, msg, force)
      case WARNING if !pos.isDefined =>
        original.warning(pos, msg)
      case WARNING if !suppressedRanges.contains(pos.source) =>
        deferredWarnings.getOrElseUpdate(pos.source, new ArrayBuffer) += ((pos, msg))
      case WARNING if suppressedRanges(pos.source).exists(_.includes(pos)) =>
        ()
      case WARNING =>
        original.warning(pos, msg)
      case ERROR => original.error(pos, msg)
    }
    updateCounts()
  }

  private def updateCounts(): Unit = {
    INFO.count = original.INFO.count
    WARNING.count = original.WARNING.count
    ERROR.count = original.ERROR.count
  }

  private def originalSeverity(severity: Severity) = severity match {
    case INFO => original.INFO
    case WARNING => original.WARNING
    case ERROR => original.ERROR
  }

  override def hasErrors: Boolean =
    original.hasErrors || cancelled

  override def hasWarnings: Boolean =
    original.hasWarnings

  override def resetCount(severity: Severity): Unit = {
    super.resetCount(severity)
    original.resetCount(originalSeverity(severity))
  }

  override def flush(): Unit = {
    super.flush()
    original.flush()
  }
}
