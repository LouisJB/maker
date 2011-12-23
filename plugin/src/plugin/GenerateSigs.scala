package plugin

import tools.nsc.plugins.{PluginComponent, Plugin}
import tools.nsc.{Phase, Global}

class GenerateSigs(val global: Global, signatures : ProjectSignatures) extends Plugin {
  import global._

  val name = "gensigs"
  val description = "signature generator"

  val components = List[PluginComponent](Component)

  object Component extends PluginComponent{
    val global: GenerateSigs.this.global.type = GenerateSigs.this.global
    val runsAfter = List[String]("typer")
    val phaseName = "generatesignatures"

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)
    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      override def run(){
        super.run
      }

      def apply(unit: CompilationUnit) {
        val collector = scala.collection.mutable.Set[String]()
        newTraverser(collector).traverse(unit.body)
        signatures += (unit.source.file.file, Set[String]() ++ collector)
      }
    }

    def newTraverser(collector : scala.collection.mutable.Set[String]): Traverser = new ForeachTreeTraverser(check(collector))

    def check(collector : scala.collection.mutable.Set[String])(tree: Tree): Unit = {
      tree match {
        case dd : DefDef if ! dd.mods.isPrivate => {
          val sig = dd.symbol.fullName + " : def, modifiers " + dd.mods.flags + ", type params " + dd.tparams + ", return type " + dd.tpt + ", params " + dd.vparamss.map(_.map(_.tpt)) 
          collector += sig
        }
        case vd : ValDef if ! vd.mods.isPrivate => {
          val sig = vd.symbol.fullName + " : val, modifiers " + vd.mods.flags + ", type " + vd.tpt
          collector += sig
        }
        case _ =>
      }
    }
  }
}
