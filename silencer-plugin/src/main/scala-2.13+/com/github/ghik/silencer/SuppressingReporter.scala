package com.github.ghik.silencer

import java.io.File

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.util.{Position, SourceFile}
import scala.reflect.io.AbstractFile
import scala.tools.nsc.reporters.{FilteringReporter, ForwardingReporter}
import scala.util.matching.Regex

class SuppressingReporter(
  original: FilteringReporter,
  globalFilters: List[Regex],
  pathFilters: List[Regex],
  sourceRoots: List[AbstractFile]
) extends ForwardingReporter(original) {

  private val deferredWarnings = new mutable.HashMap[SourceFile, ArrayBuffer[(Position, String)]]
  private val fileSuppressions = new mutable.HashMap[SourceFile, List[Suppression]]
  private val normalizedPathCache = new mutable.HashMap[SourceFile, String]

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

  def checkUnused(source: SourceFile): Unit =
    fileSuppressions(source).foreach(_.reportUnused(this))

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
    def matchesPathFilter: Boolean = pathFilters.nonEmpty && {
      val filePath = normalizedPathCache.getOrElseUpdate(pos.source, {
        val file = pos.source.file
        val relIt = sourceRoots.iterator.flatMap(relativize(_, file))
        val relPath = if (relIt.hasNext) relIt.next() else file.canonicalPath
        relPath.replaceAllLiterally("\\", "/")
      })
      anyMatches(pathFilters, filePath)
    }

    super.filter(pos, msg, severity) match {
      case 0 if severity == WARNING =>
        if (matchesPathFilter || anyMatches(globalFilters, msg))
          2
        else if (!pos.isDefined)
          0
        else if (!fileSuppressions.contains(pos.source)) {
          deferredWarnings.getOrElseUpdate(pos.source, new ArrayBuffer) += ((pos, msg))
          2
        } else if (isSuppressed(fileSuppressions(pos.source), pos, msg))
          2
        else
          0

      case n => n
    }
  }

  private def relativize(dir: AbstractFile, child: AbstractFile): Option[String] = {
    val childPath = child.canonicalPath
    val dirPath = dir.canonicalPath + File.separator
    if (childPath.startsWith(dirPath)) Some(childPath.substring(dirPath.length)) else None
  }

  private def anyMatches(patterns: List[Regex], value: String): Boolean =
    patterns.exists(_.findFirstIn(value).isDefined)
}
