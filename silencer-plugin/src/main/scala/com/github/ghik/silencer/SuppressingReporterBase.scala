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

  protected def anyMatches(patterns: List[Regex], value: String): Boolean =
    patterns.exists(_.findFirstIn(value).isDefined)
}
