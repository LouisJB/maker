package plugin

import tools.nsc.plugins.{PluginComponent, Plugin}
import tools.nsc.{Phase, Global}
import reflect.generic.ModifierFlags

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
        println("Compilation unit " + unit)
        newTraverser(collector).traverse(unit.body)
      }
    }

    def newTraverser(collector : scala.collection.mutable.Set[String]): Traverser = new ForeachTreeTraverser(check(collector))

    def check(collector : scala.collection.mutable.Set[String])(tree: Tree): Unit = {
      tree match {
        case pd : PackageDef => {
          println("Got package def " + pd.name)
          val owner =  pd.pid.symbol.owner
          println("Owner = " + owner + ", " + owner.getClass)

        }
        case cd : ClassDef => {
          println("Got class def " + cd.name + ", mods " + cd.mods + ", type params " + cd.tparams)
        }
        case dd : DefDef if ! dd.mods.isPrivate => {
          println("got def def, name " + dd.name + ", modifiers " + dd.mods + ", type params " + dd.tparams + ", return type " + dd.tpt + ", params " + dd.vparamss)
          val owner =  dd.symbol.owner
          println("Owner = " + owner + ", " + owner.getClass)
          val classOwner = owner.owner
          println("Class owner = " + classOwner + ", " + classOwner.getClass)
        }
        case _ =>
      }
    }
  }
}
