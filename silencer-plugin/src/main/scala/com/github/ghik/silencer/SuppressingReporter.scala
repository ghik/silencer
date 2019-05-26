package com.github.ghik.silencer

import java.io.File

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.util.{Position, SourceFile}
import scala.reflect.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.util.matching.Regex

class SuppressingReporter(
  original: Reporter,
  globalFilters: List[Regex],
  pathFilters: List[Regex],
  sourceRoots: List[AbstractFile]
) extends Reporter {

  private val deferredWarnings = new mutable.HashMap[SourceFile, ArrayBuffer[(Position, String)]]
  private val fileSuppressions = new mutable.HashMap[SourceFile, List[Suppression]]
  private val normalizedPathCache = new mutable.HashMap[SourceFile, String]

  def setSuppressions(source: SourceFile, suppressions: List[Suppression]): Unit = {
    fileSuppressions(source) = suppressions
    for ((pos, msg) <- deferredWarnings.remove(source).getOrElse(Seq.empty) if !suppressions.exists(_.suppresses(pos, msg))) {
      original.warning(pos, msg)
    }
    updateCounts()
  }

  override def reset(): Unit = {
    super.reset()
    original.reset()
    deferredWarnings.clear()
    fileSuppressions.clear()
  }

  protected def info0(pos: Position, msg: String, severity: Severity, force: Boolean): Unit = {
    def matchesPathFilter: Boolean = pathFilters.nonEmpty && {
      val filePath = normalizedPathCache.getOrElseUpdate(pos.source, {
        val file = pos.source.file
        val relIt = sourceRoots.iterator.flatMap(relativize(_, file))
        val relPath = if (relIt.hasNext) relIt.next() else file.canonicalPath
        relPath.replaceAllLiterally("\\", "/")
      })
      anyMatches(pathFilters, filePath)
    }

    severity match {
      case INFO =>
        original.info(pos, msg, force)
      case WARNING if matchesPathFilter || anyMatches(globalFilters, msg) =>
        ()
      case WARNING if !pos.isDefined =>
        original.warning(pos, msg)
      case WARNING if !fileSuppressions.contains(pos.source) =>
        deferredWarnings.getOrElseUpdate(pos.source, new ArrayBuffer) += ((pos, msg))
      case WARNING if fileSuppressions(pos.source).exists(_.suppresses(pos, msg)) =>
        ()
      case WARNING =>
        original.warning(pos, msg)
      case ERROR =>
        original.error(pos, msg)
    }
    updateCounts()
  }

  private def relativize(dir: AbstractFile, child: AbstractFile): Option[String] = {
    val childPath = child.canonicalPath
    val dirPath = dir.canonicalPath + File.separator
    if (childPath.startsWith(dirPath)) Some(childPath.substring(dirPath.length)) else None
  }

  private def anyMatches(patterns: List[Regex], value: String): Boolean =
    patterns.exists(_.findFirstIn(value).isDefined)

  private def updateCounts(): Unit = {
    INFO.count = original.INFO.count
    WARNING.count = original.WARNING.count
    ERROR.count = original.ERROR.count
  }

  private def originalSeverity(severity: Severity): original.Severity = severity match {
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
