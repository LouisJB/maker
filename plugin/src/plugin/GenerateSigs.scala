package plugin

import tools.nsc.plugins.{PluginComponent, Plugin}
import tools.nsc.{Phase, Global}
import reflect.generic.ModifierFlags
import maker.utils.Log

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

    def moduleSuffix(sym: Symbol) =
      if (sym.hasModuleFlag && !sym.isMethod &&
        !sym.isImplClass && !sym.isJavaDefined) "$"
      else ""

    def javaName(sym: Symbol): String =      
      if (sym.isClass || (sym.isModule && !sym.isMethod))
        sym.fullName('/') + moduleSuffix(sym)
      else
        sym.simpleName.toString.trim() + moduleSuffix(sym)

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
        case cd : ClassDef => {
          Log.info("Name of class is " + cd.name + ", full name is " + cd.symbol.fullName)
          Log.info("Another name might be " + javaName(cd.symbol))
        }
        case md : ModuleDef => {
          Log.info("Name of object is " + md.name + ", full name is " + md.symbol.fullName)
          Log.info("Another name for object " + javaName(md.symbol))
        }
        case _ =>
      }
    }
  }
}
