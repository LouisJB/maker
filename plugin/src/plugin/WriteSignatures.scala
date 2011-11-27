package plugin

import tools.nsc.plugins.{PluginComponent, Plugin}
import tools.nsc.{Phase, Global}

class WriteSignatures(val global: Global, collector : scala.collection.mutable.Map[Any, Any]) extends Plugin {
  import global._
  var symbolMap = Map[String, Set[String]]()

  val name = "traverser"
  val description = "Signature Writer"

  val components = List[PluginComponent](Component)

  object Component extends PluginComponent{
    val global: WriteSignatures.this.global.type = WriteSignatures.this.global
    val runsAfter = List[String]("refchecks")
    val phaseName = "generateclasssignatures"

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)
    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      override def run(){
        super.run
      }

      def apply(unit: CompilationUnit) {
        val collector = scala.collection.mutable.Set[String]()
        newTraverser(collector).traverse(unit.body)
        (unit.source.file.file, Set() ++ collector.map{s => new java.io.File(s)})
      }
    }

    def newTraverser(collector : scala.collection.mutable.Set[String]): Traverser = new ForeachTreeTraverser(check(collector))
    /** Generates a String identifying the provided Symbol that is stable across runs.
    * The Symbol must be a publicly accessible symbol, such as a method, class, or type member.*/
    private def stableID(sym: Symbol) =
    {
      val tpe = if(sym.isTerm) "term" else "type"
      val name = sym.fullName
      val over = if(sym.isMethod) name + "(" + sym.tpe.toString + ")" else name
      tpe + " " + over
    }
    import scala.tools.nsc.symtab.Symbols

    private def symbolPath(s : Symbol, path : List[Symbol] = Nil) : List[Symbol] = Option(s.rawowner) match {
      case Some(t) => symbolPath(s.rawowner, s :: path)
      case _ => path
    }
    private def classSig(s : Symbol) : ClassSignature = {
      val sp = symbolPath(s)
      val packages = sp.filter(_.toString.startsWith("package")).map(_.rawname.toString)
      val classes = sp.filter(_.toString.startsWith("class")).map(_.rawname.toString)
      ClassSignature(packages, classes)
    }

    var klass : ClassSignature = null

    def check(collector : scala.collection.mutable.Set[String])(tree: Tree): Unit = {
      tree match {
        case cd : ClassDef => {
          klass = classSig(cd.symbol)
        }
        case vd : ValDef =>{
          println("Got a val def")
          println(stableID(vd.symbol))

        }
        case dd : DefDef =>{
           println("Got a def def")
          println(stableID(dd.symbol))
        }
        case _ =>
      }
//      Option(tree.symbol).foreach {
//        sym =>
//          Option(sym.sourceFile).foreach {
//            sf =>
//              Option(sf.path).foreach(
//                collector += _
//              )
//          }
//      }
    }
  }
}
