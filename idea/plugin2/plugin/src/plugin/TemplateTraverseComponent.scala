package plugin

//import scala.tools.nsc._
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.plugins.Plugin

/** This class implements a plugin component using a tree
 *  traverser */
 //class TemplateTraverse(val global: Global) extends PluginComponent {
class TemplateTraverse(val global: Global) extends Plugin {
  //import global._
  import global.{CompilationUnit, Traverser, Tree, Apply, ForeachTreeTraverser, TypeTree, ForEachTypeTraverser, Type, Typed}
  import global.TypeTraverser
  import global._
  var symbolMap = Map[String, Set[String]]()

  //import global.definitions._
  val name = "traverser"
  val description = "code walker"
  
  val components = List[PluginComponent](Component)

  private object Component extends PluginComponent{
    val global: TemplateTraverse.this.global.type = TemplateTraverse.this.global
    val runsAfter = List[String]("refchecks")
    /** The phase name of the compiler plugin
    *  @todo Adapt to specific plugin.
    */
    val phaseName = "generatefiledependencies"

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)
    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      def apply(unit: CompilationUnit) {
        println("Compilation unit is " + unit + ", " + unit.getClass)
        println("Compilation unit is " + unit.source.path + ", " + unit.source)
        newTraverser().traverse(unit.body)
        //newTypeTraverser().traverse(unit.body)
      }
    }

    def newTraverser(): Traverser = new ForeachTreeTraverser(check)

    def check(tree: Tree): Unit = {
      println(tree)
      if (tree.symbol != null)
        println("\t" + tree.symbol.sourceFile)
//      tree match {
//        case Apply(fun, args) =>
//          println("traversing application of "+ fun + ",  " + tree.getClass)
//        case Typed(expr, tpt) =>
//          println("Typed exp " + expr + ", " + tpt)
//        case tt : TypeTree =>
//          tt.symbol match {
//            case cs : ClassSymbol => {
//              println( "\tclass symbol:" + tt)
//              println("\t\t enclosingPackage " + cs.enclosingPackage)
//              println("\t\t sourceFile       " + cs.sourceFile)
//              println("\t\t owner            " + cs.owner)
//              println("\t\t owner parent     " + cs.owner.owner)
//              println("Fooble fabbl fobblee")
//
//
//
//
//            }
//            case _ =>
//              println("Typed tree " + tt + ", " + tt.getClass + ", " + tt.symbol + ", " + tt.symbol.getClass)
//          }
//        case _ => ()//println(tree.getClass + ",     "  + tree)
//      }
    }
    def checkType(tp : Type) = println("Type is " + tp + ", " + tp.getClass)
    def newTypeTraverser() : TypeTraverser = new ForEachTypeTraverser(checkType)
  }
}


