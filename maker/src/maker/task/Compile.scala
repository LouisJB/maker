package maker.task

import maker.project.Project
import maker.os.Environment
import maker.utils.Log
import maker.os.Command

import maker.utils.FileUtils._
import java.io.{BufferedWriter, File}

object Compile {
  def writeCompileInstructionsFile(compileInstructionFile: File, project : Project, srcFilesToCompile: Set[File]) {
    import project._
    withFileWriter(compileInstructionFile) {
      out: BufferedWriter =>

        val pluginJar = "out/artifacts/plugin_jar/plugin.jar"
        out.write("-unchecked\n")
        out.write("-Xplugin:" + pluginJar + "\n")
        out.write("-Dmaker-project-root=" + root + "\n")
        out.write("-cp " + classpath + "\n")
        out.write("-d " + outputDir.getAbsolutePath + "\n")
        srcFilesToCompile.foreach {
          f: File => out.write(f.getAbsolutePath + "\n")
        }
    }
  }
}


case class Compile(project: Project, dependencies: List[Task] = Nil) extends Task {

  import Environment._
  import project._
  import Compile._

  val lock = new Object

  def dependsOn(tasks: Task*) = copy(dependencies = (dependencies ::: tasks.toList).distinct)


  protected def execSelf: (Int, String) = {
    if (!outputDir.exists)
      outputDir.mkdirs
    if (compileRequired) {
      Log.info("Compiling " + project)
      val compileInstructionFile = new File(root, "compile")
      val lastCompilationTime = compilationTime()

      writeCompileInstructionsFile(compileInstructionFile, project, changedSrcFiles)

      val (res, msg) = compileFromInstructionFile(compileInstructionFile)
      println(msg)
//      val changedClassFiles = classFiles.filter(_.lastModified > lastCompilationTime)

      (0, "Compiled")
    } else {
      Log.info("Already Compiling")
      (0, "Already compiled")
    }
  }


  def compileFromInstructionFile(compileInstructionFile: File): (Int, String) = {
    Command(fsc, "@" + compileInstructionFile.getAbsolutePath).exec
  }
}

