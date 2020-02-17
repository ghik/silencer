package com.github.ghik.silencer

import java.io.File

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.util.{Position, SourceFile}
import scala.reflect.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.util.matching.Regex

// Code that's shared between the version-dependent sources for 2.12 and 2.13
trait SuppressingReporterBase { self: Reporter =>
  protected def pathFilters: List[Regex]
  protected def lineContentFilters: List[Regex]
  protected def sourceRoots: List[AbstractFile]

  protected val deferredWarnings = new mutable.HashMap[SourceFile, ArrayBuffer[(Position, String)]]
  protected val fileSuppressions = new mutable.HashMap[SourceFile, List[Suppression]]
  protected val normalizedPathCache = new mutable.HashMap[SourceFile, String]

  def checkUnused(source: SourceFile): Unit =
    fileSuppressions(source).foreach(_.reportUnused(this))

  protected def relativize(dir: AbstractFile, child: AbstractFile): Option[String] = {
    val childPath = child.canonicalPath
    val dirPath = dir.canonicalPath + File.separator
    if (childPath.startsWith(dirPath)) Some(childPath.substring(dirPath.length)) else None
  }

  protected def matchesPathFilter(pos: Position): Boolean = pathFilters.nonEmpty && pos.isDefined && {
    val filePath = normalizedPathCache.getOrElseUpdate(pos.source, {
      val file = pos.source.file
      val relIt = sourceRoots.iterator.flatMap(relativize(_, file))
      val relPath = if (relIt.hasNext) relIt.next() else file.canonicalPath
      relPath.replaceAllLiterally("\\", "/")
    })
    anyMatches(pathFilters, filePath)
  }

  protected def matchesLineContentFilter(pos: Position): Boolean =
    lineContentFilters.nonEmpty && pos.isDefined &&
      anyMatches(lineContentFilters, pos.source.lines(pos.line - 1).next())

  protected def anyMatches(patterns: List[Regex], value: String): Boolean =
    patterns.exists(_.findFirstIn(value).isDefined)
}
