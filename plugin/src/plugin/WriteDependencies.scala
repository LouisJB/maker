package plugin

import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.plugins.Plugin
import java.io.{FileWriter, BufferedWriter}

object WriteDependencies{
  def dependencyFile(sourceFile : String) = {
    sourceFile.replace(".scala", ".maker-dependencies")
  }
}
/** This class implements a plugin component using a tree
 *  traverser */
 //class TemplateTraverse(val global: Global) extends PluginComponent {
class WriteDependencies(val global: Global) extends Plugin {
  import WriteDependencies._
//  import global.{CompilationUnit, Traverser, Tree, Apply, ForeachTreeTraverser, TypeTree, ForEachTypeTraverser, Type, Typed}
//  import global.TypeTraverser
  import global._
  var symbolMap = Map[String, Set[String]]()

  //import global.definitions._
  val name = "traverser"
  val description = "code walker"
  
  val components = List[PluginComponent](Component)

  private object Component extends PluginComponent{
    val global: WriteDependencies.this.global.type = WriteDependencies.this.global
    val runsAfter = List[String]("refchecks")
    /** The phase name of the compiler plugin
    *  @todo Adapt to specific plugin.
    */
    val phaseName = "generatefiledependencies"

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)
    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      def outputFile(unit : CompilationUnit) = unit.source.path.replace(".scala", ".maker-dependencies")

      def apply(unit: CompilationUnit) {
        val collector = scala.collection.mutable.Set[String]()
        newTraverser(collector).traverse(unit.body)
        println("Writing to " + dependencyFile(unit.source.path))
        val out = new BufferedWriter(new FileWriter(dependencyFile(unit.source.path)));
        collector.foreach{
          dep => out.write(dep + "\n")
        }
        out.close
        if (1 == 1)
          System.exit(99)
      }
    }

    def newTraverser(collector : scala.collection.mutable.Set[String]): Traverser = new ForeachTreeTraverser(check(collector))

    def check(collector : scala.collection.mutable.Set[String])(tree: Tree): Unit = {
      Option(tree.symbol).foreach {
        sym =>
          Option(sym.sourceFile).foreach {
            sf =>
              Option(sf.path).foreach(
                collector += _
              )
          }
      }
    }
  }
}


