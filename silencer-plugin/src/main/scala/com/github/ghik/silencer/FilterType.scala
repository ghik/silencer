package com.github.ghik.silencer

import scala.util.matching.Regex
import com.github.ghik.silencer.FilterType.{RegexFilter, StringFilter}

sealed trait FilterType {
  def name: String

  def parsePattern(pattern: String): Either[String, Regex] =
    this match {
      case _: RegexFilter => Right(pattern.r)
      case _: StringFilter => Left(pattern)
    }
}

object FilterType {

  val all = Array(MessageFilters, PathFilters, SourceRootFilters)

  def parseName(name: String): Option[FilterType] =
    name match {
      case MessageFilters.name => Some(MessageFilters)
      case PathFilters.name => Some(PathFilters)
      case SourceRootFilters.name => Some(SourceRootFilters)
      case _ => None
    }

  sealed trait RegexFilter extends FilterType
  sealed trait StringFilter extends FilterType

  object MessageFilters extends RegexFilter {
    val name: String = "globalFilters"
  }

  object PathFilters extends RegexFilter {
    val name: String = "globalPathFilters"
  }

  object SourceRootFilters extends StringFilter {
    val name: String = "sourceRootFilters"
  }

}
