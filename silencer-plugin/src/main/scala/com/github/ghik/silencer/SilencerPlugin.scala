package com.github.ghik.silencer

import java.util.regex.PatternSyntaxException

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.internal.util.Position
import scala.reflect.io.AbstractFile
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase, Properties}
import scala.util.matching.Regex

class SilencerPlugin(val global: Global) extends Plugin with SilencerPluginCompat { plugin =>

  import global._

  val name = "silencer"
  val description = "Scala compiler plugin for warning suppression"
  val components: List[PluginComponent] = List(extractSuppressions, checkUnusedSuppressions)

  private final val InitDefault1 = TermName("<init>$default$1").encodedName
  private val scala211: Boolean = Properties.versionNumberString.startsWith("2.11")

  private val globalFilters = ListBuffer.empty[Regex]
  private val lineContentFilters = ListBuffer.empty[Regex]
  private val pathFilters = ListBuffer.empty[Regex]
  private val sourceRoots = ListBuffer.empty[AbstractFile]
  private var checkUnused = false
  private var searchMacroExpansions = false

  private lazy val reporter = new SuppressingReporter(global.reporter,
    globalFilters.result(), lineContentFilters.result(), pathFilters.result(), sourceRoots.result())

  private def split(s: String): Iterator[String] = s.split(';').iterator

  override def processOptions(options: List[String], error: String => Unit): Unit = {
    options.foreach(_.split("=", 2) match {
      case Array("globalFilters", pattern) =>
        globalFilters ++= split(pattern).map(_.r)
      case Array("lineContentFilters", pattern) =>
        lineContentFilters ++= split(pattern).map(_.r)
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
      case Array("searchMacroExpansions") =>
        searchMacroExpansions = true
      case _ =>
    })

    global.reporter = reporter
  }

  override val optionsHelp: Option[String] = Some(
    """  -P:silencer:globalFilters=...  Semicolon separated regexes for filtering warning messages globally
      |  -P:silencer:pathFilters=...    Semicolon separated regexes for filtering source paths
      |  -P:silencer:sourceRoots=...    Semicolon separated paths of source root directories to relativize path filters
      |  -P:silencer:checkUnused        Enables reporting of unused @silent annotations
    """.stripMargin)

  private object extractSuppressions extends PluginComponent {
    val global: plugin.global.type = plugin.global
    val runsAfter = List("typer")
    override val runsBefore = List("patmat")
    val phaseName = "silencer"
    override def description: String = "inspect @silent annotations for warning suppression"

    private lazy val silentSym =
      try rootMirror.staticClass("com.github.ghik.silencer.silent") catch {
        case _: ScalaReflectionException => NoSymbol
      }

    private lazy val compatNowarnSym =
      if (!scala211) NoSymbol // leave @nowarn to be processed by scalac
      else try rootMirror.staticClass("scala.annotation.nowarn") catch {
        case _: ScalaReflectionException => NoSymbol
      }

    def newPhase(prev: Phase): StdPhase = {
      new StdPhase(prev) {
        def apply(unit: CompilationUnit): Unit = {
          if (silentSym == NoSymbol && compatNowarnSym == NoSymbol && globalFilters.isEmpty && pathFilters.isEmpty) {
            plugin.reporter.warning(NoPosition,
              "`silencer-plugin` was enabled but @silent annotation was not found on classpath" +
                " - have you added `silencer-lib` as a library dependency?"
            )
          }
          applySuppressions(unit)
        }
      }
    }

    def applySuppressions(unit: CompilationUnit): Unit = {
      val suppressions = if (silentSym == NoSymbol && compatNowarnSym == NoSymbol) Nil else {
        def mkSuppression(tree: Tree, annot: Tree, annotPos: Position, inMacroExpansion: Boolean): Suppression = {
          val annotSym = annot.tpe.typeSymbol
          val range = treeRangePos(tree)
          val msgPattern = annot match {
            case Apply(_, Nil) => None
            case Apply(_, List(MaybeNamedArg(Literal(Constant(arg: String))))) =>
              // partial support for Scala 2.13.2 @nowarn annotation
              // only interpreting the 'msg' filter, other filters simply suppress everything
              val regexOpt =
                if (annotSym == compatNowarnSym)
                  arg.split("&").iterator.filter(_.startsWith("msg=")).map(_.substring(4)).toList.headOption
                else
                  Some(arg)

              regexOpt.flatMap { regex =>
                try Some(regex.r) catch {
                  case pse: PatternSyntaxException =>
                    reporter.error(annotPos, s"invalid message pattern $regex in @${annotSym.nameString} annotation: ${pse.getMessage}")
                    None
                }
              }
            case Apply(_, List(Select(pref, InitDefault1)))
              if annotSym == compatNowarnSym && pref.symbol == compatNowarnSym.companion =>
              None
            case _ =>
              reporter.error(annotPos, s"expected literal string as @${annotSym.nameString} annotation argument")
              None
          }
          new Suppression(annotPos, range, msgPattern, inMacroExpansion)
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
          private val suppressionPositionsVisited = new mutable.HashSet[Int]
          private var inMacroExpansion: Boolean = false

          private def addSuppression(tree: Tree, annot: Tree, annotPos: Position): Unit = {
            if (annot.tpe eq null) return // issue #56
            val actualAnnotPos = if (annotPos != NoPosition) annotPos else tree.pos
            val annotSym = annot.tpe.typeSymbol
            if (annotSym != NoSymbol && (annotSym == silentSym || annotSym == compatNowarnSym) &&
              suppressionPositionsVisited.add(actualAnnotPos.point)
            ) {
              suppressionsBuf += mkSuppression(tree, annot, actualAnnotPos, inMacroExpansion)
            }
          }

          override def traverse(t: Tree): Unit = {
            val expandee = analyzer.macroExpandee(t)
            val macroExpansion = expandee != EmptyTree && expandee != t
            if (macroExpansion) {
              traverse(expandee)
            }
            if (!macroExpansion || searchMacroExpansions) {
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
                  val annots = md.symbol.annotations
                  // search for @silent annotations in trees of other annotations
                  // you would expect that super.traverse should do that but it doesn't because apparently
                  // at typer phase annotations are no longer available in the tree itself and must be fetched from symbol
                  annots.foreach(ai => traverse(ai.tree))
                  annots.foreach(ai => addSuppression(md, ai.tree, ai.pos))
                case _ =>
              }

              inMacroExpansion = wasInMacroExpansion
            }
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
    override def description: String = "report unused @silent/@nowarn annotations"

    def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      def apply(unit: CompilationUnit): Unit =
        if (checkUnused) {
          reporter.checkUnused(unit.source)
        }
    }
  }
}
