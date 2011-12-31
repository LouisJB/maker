package plugin

import tools.nsc.plugins.{PluginComponent, Plugin}
import tools.nsc.{Phase, Global}
import reflect.generic.ModifierFlags
import maker.utils.Log
import java.io.File
import maker._
import maker.utils.FileUtils._

/**
 * Note that the map updated has both classes and companion objects, regardless of whether
 * both actually exist. The compiler rules for this seemed complex. 
 * The current purpose of this map is to delete class files when their associated
 * source file is deleted, the map generated should be sufficient for that
 */
class DetermineClassFiles(val global: Global, outputDir : File, sourceToClassFiles : SourceClassFileMapping) extends Plugin {
  import global._

  val name = "determineclassfiles"
  val description = "generate a map of source file to class files"

  val components = List[PluginComponent](Component)

  object Component extends PluginComponent{
    val global: DetermineClassFiles.this.global.type = DetermineClassFiles.this.global
    val runsAfter = List[String]("icode")
    val phaseName = name

    def newPhase(prev: Phase): Phase = new TraverserPhase(prev)

    class TraverserPhase(prev: Phase) extends StdPhase(prev) {

      override def run(){
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

      def apply(unit: CompilationUnit) {}
    }

    def isObject(sym : Symbol) = sym.hasModuleFlag && !sym.isMethod && !sym.isImplClass && !sym.isJavaDefined
  }
}
