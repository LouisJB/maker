package plugin

import scala.tools.nsc.plugins.{PluginComponent, Plugin}
import scala.tools.nsc.{Phase, Global}
import scala.collection.mutable.{Set=>MSet}
import utils.{SourceClassFileMapping, ClassFileDependencies, ProjectSignatures}
import maker.utils.FileUtils._
import java.io.File

case class GenerateCompilationMetadataComponent (
  val global: Global,
  signatures : ProjectSignatures,
  dependencies : ClassFileDependencies,
  outputDir : File,
  sourceToClassFiles : SourceClassFileMapping
) extends PluginComponent {

  import global._
    val runsAfter = List[String]("icode")

    val phaseName = "generateMakerDepsAndSigs"

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)

    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      /**
       * Note that the map updated has both classes and companion objects, regardless of whether
       * both actually exist. The compiler rules for this seemed complex.
       * The current purpose of this map is to delete class files when their associated
       * source file is deleted, the map generated should be sufficient for that
       */
      override def run() {
        global.icodes.classes.foreach{
          case (c, i) =>
            val className = c.fullName.replace('.', '/')
            val objectName = className + "$"
            Set(className, objectName).foreach{
              cn =>
                sourceToClassFiles += (file(i.cunit.source.file.path), file(outputDir,  cn  + ".class"))
            }
        }
        super.run
      }

      // after the phase extract dependencies and signatures from the tree
      def apply(unit: CompilationUnit) {
        val compilerResults = new CompilerPhaseResults()
        newTraverser(compilerResults).traverse(unit.body)
        signatures += (unit.source.file.file, Set[String]() ++ compilerResults.sigs)
      }

      def newTraverser(collector : CompilerPhaseResults): Traverser = new ForeachTreeTraverser(treeProcessor(collector))
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
