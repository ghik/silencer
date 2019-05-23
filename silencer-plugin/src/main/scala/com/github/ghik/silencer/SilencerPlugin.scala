package com.github.ghik.silencer

import scala.collection.mutable.ListBuffer
import scala.reflect.internal.util.Position
import scala.reflect.io.AbstractFile
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}
import scala.util.matching.Regex

class SilencerPlugin(val global: Global) extends Plugin { plugin =>

  import global._

  val name = "silencer"
  val description = "Scala compiler plugin for warning suppression"
  val components: List[PluginComponent] = List(component)

  private val globalFilters = ListBuffer.empty[Regex]
  private val pathFilters = ListBuffer.empty[Regex]
  private val sourceRoots = ListBuffer.empty[AbstractFile]

  private lazy val reporter =
    new SuppressingReporter(global.reporter, globalFilters.result(), pathFilters.result(), sourceRoots.result())

  private def split(s: String): Iterator[String] = s.split(';').iterator

  override def processOptions(options: List[String], error: String => Unit): Unit = {
    options.foreach(_.split("=", 2) match {
      case Array("globalFilters", pattern) =>
        globalFilters ++= split(pattern).map(_.r)
      case Array("pathFilters", pattern) =>
        pathFilters ++= split(pattern).map(_.r)
      case Array("sourceRoots", rootPaths) =>
        sourceRoots ++= split(rootPaths).flatMap { path =>
          val res = Option(AbstractFile.getDirectory(path))
          if (res.isEmpty) {
            reporter.warning(NoPosition, s"Invalid source root: $path is not a directory")
          }
          res
        }
      case _ =>
    })

    global.reporter = reporter
  }

  override val optionsHelp: Option[String] = Some(
    """  -P:silencer:globalFilters=...  Semicolon separated regexes for filtering warning messages globally
      |  -P:silencer:pathFilters=...    Semicolon separated regexes for filtering source paths
      |  -P:silencer:sourceRoots=...    Semicolon separated paths of source root directories to relativize path filters
    """.stripMargin)

  private object component extends PluginComponent {
    val global: plugin.global.type = plugin.global
    val runsAfter = List("typer")
    override val runsBefore = List("patmat")
    val phaseName = "silencer"

    private lazy val silentSym = try rootMirror.staticClass("com.github.ghik.silencer.silent") catch {
      case _: ScalaReflectionException =>
        if (globalFilters.isEmpty && pathFilters.isEmpty) {
          plugin.reporter.warning(NoPosition,
            "`silencer-plugin` was enabled but the @silent annotation was not found on classpath" +
              " - have you added `silencer-lib` as a library dependency?"
          )
        }
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

        val suppressedRangesBuffer = collection.mutable.ListBuffer[Position]()
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
        def addSuppressed(t: Tree) = {
          suppressedRangesBuffer += treeRangePos(t)
        }
        object FindSuppressed extends Traverser {
          override def traverse(t: Tree) = {
            val expandee = analyzer.macroExpandee(t)
            if (expandee != EmptyTree && expandee != t) traverse(expandee)

            t match {
              case Annotated(annot, arg) if isSilentAnnot(annot) =>
                addSuppressed(arg)
              case typed@Typed(_, tpt) if tpt.tpe != null && tpt.tpe.annotations.exists(ai => isSilentAnnot(ai.tree)) =>
                addSuppressed(typed)
              case md: MemberDef if md.symbol.annotations.exists(ai => isSilentAnnot(ai.tree)) =>
                addSuppressed(md)
              case _ =>
            }
            super.traverse(t)
          }
        }
        FindSuppressed.traverse(unit.body)

        suppressedRangesBuffer.toList
      }

      plugin.reporter.setSuppressedRanges(unit.source, suppressedRanges)
    }
  }

}
