package maker.task

import maker.project.Project
import maker.os.Environment
import maker.utils.Log
import maker.os.Command

import maker.utils.FileUtils._
import java.io.{BufferedWriter, File}



case class Compile(project: Project, dependencies: List[Task] = Nil) extends Task {

  import Environment._
  import project._

  val lock = new Object

  def dependsOn(tasks: Task*) = copy(dependencies = (dependencies ::: tasks.toList).distinct)


  protected def execSelf: (Int, String) = {
    if (!outputDir.exists)
      outputDir.mkdirs
    if (compileRequired) {
      Log.info("Compiling " + project)
      new project.compiler.Run() compile changedSrcFiles.toList.map(_.getPath)

      (0, "Compiled")
    } else {
      Log.info("Already Compiling")
      (0, "Already compiled")
    }
  }


}

