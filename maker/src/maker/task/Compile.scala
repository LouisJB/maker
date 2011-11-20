package maker.task

import maker.project.Project
import maker.os.Environment
import maker.utils.Log
import java.io.File
import collection.Set


case class Compile(project: Project, changedSrcFiles : () => Set[File], dependentTasks: List[Task[_]] = Nil) extends Task[Set[File]] {

  import Environment._
  import project._

  val lock = new Object

  def dependsOn(tasks: Task[_]*) = new Compile(project, changedSrcFiles, dependentTasks = (dependentTasks ::: tasks.toList).distinct)


  protected def execSelf: Either[TaskFailed, Set[File]] = {
    if (!outputDir.exists)
      outputDir.mkdirs
    val modifiedSrcFiles = changedSrcFiles()
    if (! modifiedSrcFiles.isEmpty) {
      Log.info("Compiling changed source files for " + project)
      reporter.reset
      // First compile those files who have changed
      new compiler.Run() compile modifiedSrcFiles.toList.map(_.getPath)
      // Determine which source files have changed signatures

      val sourceFilesWithChangedSigs: Set[File] = Set() ++ project.updateSignatures
      val dependentFiles = dependencies.dependentFiles(sourceFilesWithChangedSigs)
      new compiler.Run() compile dependentFiles.toList.map(_.getPath)
      if (reporter.hasErrors)
        Left(TaskFailed(this, "Failed to compile"))
      else
        Right(modifiedSrcFiles ++ dependentFiles)
    } else {
      Log.info("Already Compiled " + project.name)
      Right(Set[File]())
    }
  }
}

