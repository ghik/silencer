package com.github.ghik.silencer

import scala.reflect.internal.util.Position
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

class SilencerPlugin(val global: Global) extends Plugin { plugin =>
  val name = "SilencerPlugin"
  val description = "Scala compiler plugin for warning suppression"
  val components: List[PluginComponent] = List(component)

  private val reporter = new SuppressingReporter(global.reporter)
  global.reporter = reporter

  private object component extends PluginComponent {
    val global = plugin.global
    val runsAfter = List("typer")
    override val runsBefore = List("patmat")
    val phaseName = "silencer"

    import global._

    private lazy val silentSym = try rootMirror.staticClass("com.github.ghik.silencer.silent") catch {
      case _: ScalaReflectionException => NoSymbol
    }

    def newPhase(prev: Phase) = new StdPhase(prev) {
      def apply(unit: CompilationUnit) = applySuppressions(unit)
    }

    // if silent annotation is on on the classpath, do nothing - the project wouldn't compile anyway if annotation was used
    def applySuppressions(unit: CompilationUnit): Unit = if (silentSym != NoSymbol) {
      val silentAnnotType = TypeRef(NoType, silentSym, Nil)
      def isSilentAnnot(tree: Tree) =
        silentSym != NoSymbol && tree.tpe != null && tree.tpe <:< silentAnnotType

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

      val suppressedRanges = suppressedTrees.map(treeRangePos)

      plugin.reporter.setSuppressedRanges(unit.source, suppressedRanges)
    }
  }

}
