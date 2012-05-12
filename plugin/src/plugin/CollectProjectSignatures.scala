package plugin

import scala.tools.nsc.plugins.{PluginComponent, Plugin}
import scala.tools.nsc.{Phase, Global}
import scala.collection.mutable.{Set=>MSet}
import utils.{SourceClassFileMapping, ClassFileDependencies, ProjectSignatures}
import maker.utils.FileUtils._
import java.io.File

// convenience class to collect signatures and dependencies from tree traverser
class CompilerPhaseResults(
  var sigs : MSet[String] = MSet[String](),
  var deps : MSet[String] = MSet[String]()
)

case class CollectProjectSignatures (
  val global: Global,
  signatures : ProjectSignatures
) extends PluginComponent {

  import global._
    val runsAfter = List[String]("icode")

    val phaseName = "generateMakerDepsAndSigs"

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)

    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      // after the phase extract dependencies and signatures from the tree
      def apply(unit: CompilationUnit) {
        val compilerResults = new CompilerPhaseResults()
        newTraverser(compilerResults).traverse(unit.body)
        signatures += (unit.source.file.file, Set[String]() ++ compilerResults.sigs)
      }

      private def newTraverser(collector : CompilerPhaseResults): Traverser = new ForeachTreeTraverser(treeProcessor(collector))
    }

    // collects up signatures and dependencies in a single call
    def treeProcessor(collector : CompilerPhaseResults)(tree: Tree): Unit = {

      // collect the non-private signatures
      tree match {
        case dd : DefDef if ! dd.mods.isPrivate => {
          val sig = dd.symbol.fullName + " : def, modifiers " + dd.mods.flags + ", type params " + dd.tparams + ", return type " + dd.tpt + ", params " + dd.vparamss.map(_.map(_.tpt)) 
          collector.sigs += sig
        }
        case vd : ValDef if ! vd.mods.isPrivate => {
          val sig = vd.symbol.fullName + " : val, modifiers " + vd.mods.flags + ", type " + vd.tpt
          collector.sigs += sig
        }
        case _ =>
      }
    }
}
