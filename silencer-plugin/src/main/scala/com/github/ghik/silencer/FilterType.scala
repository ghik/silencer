package com.github.ghik.silencer

sealed trait FilterType {
  def name: String
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

  object MessageFilters extends FilterType {
    val name: String = "globalFilters"
  }

  object PathFilters extends FilterType {
    val name: String = "globalPathFilters"
  }

  object SourceRootFilters extends FilterType {
    val name: String = "sourceRootFilters"
  }

}
