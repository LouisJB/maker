package maker.task

import maker.project.Project
import maker.os.Environment
import maker.utils.Log
import java.io.File
import collection.Set


case class Compile(project: Project, dependentTasks: List[Task[_]] = Nil) extends Task[Set[File]] {

  import Environment._
  import project._

  val lock = new Object

  def dependsOn(tasks: Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)


  protected def execSelf: Either[TaskFailed, Set[File]] = {
    if (!outputDir.exists)
      outputDir.mkdirs
    if (compileRequired) {
      Log.info("Compiling changed source files for " + project)
      val modifiedSrcFiles = project.changedSrcFiles
      // First compile those files who have changed
      new compiler.Run() compile modifiedSrcFiles.toList.map(_.getPath)
      // Determine which source files have changed signatures

      val sourceFilesWithChangedSigs: Set[File] = Set() ++ project.updateSignatures
      val dependentFiles = dependencies.dependentFiles(sourceFilesWithChangedSigs)
      new compiler.Run() compile dependentFiles.toList.map(_.getPath)
      Right(modifiedSrcFiles ++ dependentFiles)
    } else {
      Log.info("Already Compiled")
      Right(Set[File]())
    }
  }


}

