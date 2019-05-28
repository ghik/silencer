package com.github.ghik.silencer

import java.util.regex.PatternSyntaxException

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
  val components: List[PluginComponent] = List(extractSuppressions, checkUnusedSuppressions)

  private val globalFilters = ListBuffer.empty[Regex]
  private val pathFilters = ListBuffer.empty[Regex]
  private val sourceRoots = ListBuffer.empty[AbstractFile]
  private var checkUnused = false

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
      case Array("checkUnused") =>
        checkUnused = true
      case _ =>
    })

    global.reporter = reporter
  }

  override val optionsHelp: Option[String] = Some(
    """  -P:silencer:globalFilters=...  Semicolon separated regexes for filtering warning messages globally
      |  -P:silencer:pathFilters=...    Semicolon separated regexes for filtering source paths
      |  -P:silencer:sourceRoots=...    Semicolon separated paths of source root directories to relativize path filters
      |  -P:silencer:checkUnused        Enables checking whether @silence annotation actually suppressed anything
    """.stripMargin)

  private object extractSuppressions extends PluginComponent {
    val global: plugin.global.type = plugin.global
    val runsAfter = List("typer")
    override val runsBefore = List("patmat")
    val phaseName = "silencer"
    override def description: String = "inspect @silent annotations for warning suppression"

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
      val suppressions = if (silentSym == NoSymbol) Nil else {
        val silentAnnotType = TypeRef(NoType, silentSym, Nil)
        def isSilentAnnot(tree: Tree) =
          tree.tpe != null && tree.tpe <:< silentAnnotType

        def mkSuppression(tree: Tree, annot: Tree, annotPos: Position, inMacroExpansion: Boolean): Suppression = {
          val range = treeRangePos(tree)
          val actualAnnotPos = if (annotPos != NoPosition) annotPos else annot.pos
          val msgPattern = annot match {
            case Apply(_, Nil) => None
            case Apply(_, List(Literal(Constant(regex: String)))) =>
              try Some(regex.r) catch {
                case pse: PatternSyntaxException =>
                  reporter.error(actualAnnotPos, s"invalid message pattern $regex in @silent annotation: ${pse.getMessage}")
                  None
              }
            case _ =>
              reporter.error(actualAnnotPos, "expected literal string as @silent annotation argument")
              None
          }
          new Suppression(actualAnnotPos, range, msgPattern, inMacroExpansion)
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
          end = end max start
          Position.range(unit.source, start, start, end)
        }

        val suppressionsBuf = new ListBuffer[Suppression]

        object FindSuppressions extends Traverser {
          private var inMacroExpansion: Boolean = false

          private def addSuppression(tree: Tree, annot: Tree, annotPos: Position): Unit =
            if (isSilentAnnot(annot)) {
              suppressionsBuf += mkSuppression(tree, annot, annotPos, inMacroExpansion)
            }

          override def traverse(t: Tree): Unit = {
            val expandee = analyzer.macroExpandee(t)
            val macroExpansion = expandee != EmptyTree && expandee != t
            if (macroExpansion) {
              traverse(expandee)
            }

            val wasInMacroExpansion = inMacroExpansion
            inMacroExpansion = inMacroExpansion || macroExpansion

            //NOTE: it's important to first traverse the children so that nested suppression ranges come before
            //containing suppression ranges
            super.traverse(t)
            t match {
              case Annotated(annot, arg) =>
                addSuppression(arg, annot, annot.pos)
              case typed@Typed(_, tpt) if tpt.tpe != null =>
                tpt.tpe.annotations.foreach(ai => addSuppression(typed, ai.tree, ai.pos))
              case md: MemberDef =>
                md.symbol.annotations.foreach(ai => addSuppression(md, ai.tree, ai.pos))
              case _ =>
            }

            inMacroExpansion = wasInMacroExpansion
          }
        }

        FindSuppressions.traverse(unit.body)
        suppressionsBuf.toList
      }

      plugin.reporter.setSuppressions(unit.source, suppressions)
    }
  }

  private object checkUnusedSuppressions extends PluginComponent {
    val global: plugin.global.type = plugin.global
    val runsAfter = List("jvm")
    override val runsBefore = List("terminal")
    val phaseName = "silencerCheckUnused"
    override def description: String = "report unused @silent annotations"

    def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      def apply(unit: CompilationUnit): Unit =
        if (checkUnused) {
          reporter.checkUnused(unit.source)
        }
    }
  }
}
