package com.github.ghik.silencer

import java.io.File
import scala.util.matching.Regex
import scala.collection.mutable
import mutable.ArrayBuffer
import scala.annotation.tailrec
import scala.reflect.internal.util.{Position, SourceFile}
import scala.tools.nsc.reporters.Reporter

class SuppressingReporter(original: Reporter, globalFilters: List[Regex], pathFilters: List[Regex], sourceRoots: List[File]) extends Reporter {

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
    val absoluteFile = pos.source.file.file
    val relativePathOpt = sourceRoots.collectFirst {
      case sourceRoot if directoryContains(sourceRoot, absoluteFile) =>
        sourceRoot.toURI.relativize(absoluteFile.toURI).getPath
    }
    val relativePath = relativePathOpt.getOrElse(absoluteFile.getPath).replaceAllLiterally("\\", "/")

    severity match {
      case INFO =>
        original.info(pos, msg, force)
      case WARNING if existsIn(pathFilters, relativePath) || existsIn(globalFilters, msg) =>
        ()
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

  @tailrec
  private def directoryContains(dir: File, child: File): Boolean =
    if(child == null) false
    else if(dir == child) true
    else directoryContains(dir, child.getParentFile)

  private def existsIn(filters: List[Regex], source: String): Boolean =
    filters.exists(_.findFirstIn(source).isDefined)

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
