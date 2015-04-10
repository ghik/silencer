package rjghik.silencer

import scala.reflect.internal.util.Position
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

class SilencerPlugin(val global: Global) extends Plugin {
  plugin =>

  val name = "SilencerPlugin"
  val description = "Scala compiler plugin for warning suppression"
  val components: List[PluginComponent] = List(component)

  private val reporter = new SuppressingReporter(global.reporter)
  global.reporter = reporter

  private object component extends PluginComponent {
    val global = plugin.global
    val runsAfter = List("typer")
    val phaseName = "silencer"

    import global._

    def newPhase(prev: Phase) = new StdPhase(prev) {
      def apply(unit: CompilationUnit) = applySuppressions(unit)
    }

    def applySuppressions(unit: CompilationUnit): Unit = {
      val silentAnnotType = typeOf[silent]
      def isSilentAnnot(tree: Tree) =
        tree.tpe != null && tree.tpe <:< silentAnnotType

      val suppressedTrees = unit.body.collect {
        case Annotated(annot, tree) if isSilentAnnot(annot) => tree
        case Typed(tree, tpt) if tpt.tpe.annotations.exists(ai => isSilentAnnot(ai.tree)) => tree
        case md: MemberDef if md.symbol.annotations.exists(ai => isSilentAnnot(ai.tree)) => md
      }

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
        Position.range(unit.source, start, start, end)
      }

      val suppressedRanges = suppressedTrees.map(treeRangePos)

      plugin.reporter.setSuppressedRanges(unit.source, suppressedRanges)
    }
  }
}

