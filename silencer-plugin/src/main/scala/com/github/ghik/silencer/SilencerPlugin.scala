package com.github.ghik.silencer

import scala.reflect.internal.util.Position
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}
import scala.util.matching.Regex

class SilencerPlugin(val global: Global) extends Plugin {
  plugin =>
  val name = "silencer"
  val description = "Scala compiler plugin for warning suppression"
  val components: List[PluginComponent] = List(component)
  private var filters = FilterType.all.map(_ -> List.empty[Either[String, Regex]]).toMap

  private lazy val reporter =
    new SuppressingReporter(global.reporter, filters)

  override def processOptions(options: List[String], error: String => Unit): Unit = {
    options.foreach { opt =>
      val (filterName, regexString) = opt.span(_ > '=')
      for {
        filterType <- FilterType.parseName(filterName)
        previousPatterns <- filters.get(filterType)
        currentPatternList = regexString.drop(1).split(';').map(filterType.parsePattern)
        if currentPatternList.nonEmpty
      }
        filters = filters + (filterType -> (previousPatterns ++ currentPatternList))
    }

    filters.foreach { case (filterType, regexList) if regexList.nonEmpty =>
      global.inform(s"Silencer using ${filterType.name}: ${regexList.mkString(",")}")
    }

    global.reporter = reporter
  }

  override val optionsHelp: Option[String] = Some(
    """  -P:silencer:globalFilters=...             Semi-colon separated patterns to filter the warning messages
      |  -P:silencer:globalPathFilters=...         Semi-colon separated patterns to filter the source file paths
      |  -P:silencer:sourceRootFilters=...         Semi-colon separated patterns for detection of the source root
    """.stripMargin)

  private object component extends PluginComponent {
    val global: plugin.global.type = plugin.global
    val runsAfter = List("typer")
    override val runsBefore = List("patmat")
    val phaseName = "silencer"

    import global._

    private lazy val silentSym = try rootMirror.staticClass("com.github.ghik.silencer.silent") catch {
      case _: ScalaReflectionException =>
        plugin.reporter.warning(NoPosition,
          "`silencer-plugin` was enabled but the @silent annotation was not found on classpath" +
            " - have you added `silencer-lib` as a library dependency?"
        )
        NoSymbol
    }

    def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      def apply(unit: CompilationUnit): Unit = applySuppressions(unit)
    }

    def applySuppressions(unit: CompilationUnit): Unit = {
      val suppressedRanges = if (silentSym == NoSymbol) Nil else {
        val silentAnnotType = TypeRef(NoType, silentSym, Nil)
        def isSilentAnnot(tree: Tree) =
          tree.tpe != null && tree.tpe <:< silentAnnotType

        def suppressedTree(tree: Tree) = tree match {
          case Annotated(annot, arg) if isSilentAnnot(annot) => Some(arg)
          case typed@Typed(_, tpt) if tpt.tpe != null && tpt.tpe.annotations.exists(ai => isSilentAnnot(ai.tree)) => Some(typed)
          case md: MemberDef if md.symbol.annotations.exists(ai => isSilentAnnot(ai.tree)) => Some(md)
          case _ => None
        }

        def allTrees(tree: Tree): Iterator[Tree] =
          Iterator(tree, analyzer.macroExpandee(tree)).filter(_ != EmptyTree)
            .flatMap(t => Iterator(t) ++ t.children.iterator.flatMap(allTrees))

        val suppressedTrees = allTrees(unit.body).flatMap(suppressedTree).toList

        def treeRangePos(tree: Tree): Position = {
          // compute approximate range
          var start = unit.source.length
          var end = 0
          tree.foreach { child =>
            val pos = child.pos
            if (pos.isDefined) {
              start = start min pos.start
              end = end max pos.end
            }
          }
          end = end max start
          Position.range(unit.source, start, start, end)
        }

        suppressedTrees.map(treeRangePos)
      }

      plugin.reporter.setSuppressedRanges(unit.source, suppressedRanges)
    }
  }

}
