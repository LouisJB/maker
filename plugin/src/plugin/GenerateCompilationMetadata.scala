package plugin

import tools.nsc.plugins.{PluginComponent, Plugin}
import tools.nsc.{Phase, Global}
import scala.collection.mutable.{Set=>MSet}
import utils.{ClassFileDependencies, ProjectSignatures}

class CompilerPhaseResults(
  var sigs : MSet[String] = MSet[String](),
  var deps : MSet[String] = MSet[String]()
)

/**
 * generates maker dependency and signature files to enable incremental compilation
 */
class GenerateCompilationMetadata(
    val global: Global,
    signatures : ProjectSignatures,
    deps : ClassFileDependencies) extends Plugin {

  import global._

  val name = "maker_sigs_and_deps"
  val description = "signature & dependencies generator"

  val components = List[PluginComponent](Component)

  object Component extends PluginComponent{
    val global: GenerateCompilationMetadata.this.global.type = GenerateCompilationMetadata.this.global
    val runsAfter = List[String]("refchecks")
    val phaseName = "generateMakerDepsAndSigs"

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)

    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      override def run() = super.run

      def apply(unit: CompilationUnit) {
        val compilerResults = new CompilerPhaseResults()
        newTraverser(compilerResults).traverse(unit.body)
        signatures += (unit.source.file.file, Set[String]() ++ compilerResults.sigs)
      }

      def newTraverser(collector : CompilerPhaseResults): Traverser = new ForeachTreeTraverser(check(collector))
    }

    def check(collector : CompilerPhaseResults)(tree: Tree): Unit = {

      // collect the signatures
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

      // collect the dependencies
      Option(tree.symbol).foreach {
        sym =>
          Option(sym.sourceFile).foreach {
            sf =>
              Option(sf.path).foreach(
                collector.deps += _
              )
          }
      }
    }
  }
}
