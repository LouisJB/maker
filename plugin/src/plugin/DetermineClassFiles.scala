package plugin

import tools.nsc.plugins.{PluginComponent, Plugin}
import tools.nsc.{Phase, Global}
import reflect.generic.ModifierFlags
import maker.utils.Log
import java.io.File

class DetermineClassFiles(val global: Global, sourceToClassFiles : SourceClassFileMapping) extends Plugin {
  import global._

  val name = "determineclassfiles"
  val description = "generate a map of source file to class files"

  val components = List[PluginComponent](Component)

  object Component extends PluginComponent{
    val global: DetermineClassFiles.this.global.type = DetermineClassFiles.this.global
    val runsAfter = List[String]("typer")
    val phaseName = name

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)
    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      override def run(){
        super.run
      }

      def apply(unit: CompilationUnit) {
        val collector = scala.collection.mutable.Set[String]()
        newTraverser(collector).traverse(unit.body)
      sourceToClassFiles ++= (unit.source.file.file, Set[File]() ++ collector.map{name => new File(name + ".class")})
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
        case cd : ClassDef => {
          collector += javaName(cd.symbol)
        }
        case md : ModuleDef => {
          collector += javaName(md.symbol)
        }
        case _ =>
      }
    }
  }
}

