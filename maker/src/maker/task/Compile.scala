package maker.task

import maker.project.Project
import maker.os.Environment
import maker.utils.Log
import java.io.File
import collection.Set
import scala.tools.nsc.reporters.AbstractReporter
import scala.tools.nsc.Global

abstract class AbstractCompile(project : Project) extends Task[Set[File]]{
  val lock = new Object
  def outputDir : File
  def changedSrcFiles() : Set[File]

  protected def doCompilation(compiler : Global): Either[TaskFailed, Set[File]] = {
    val reporter = compiler.reporter
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
      val dependentFiles = project.dependencies.dependentFiles(sourceFilesWithChangedSigs)
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

case class Compile(project: Project, dependentTasks: List[Task[_]] = Nil) extends AbstractCompile(project) {

  import Environment._

  protected def execSelf = doCompilation(project.compiler)
  def changedSrcFiles = project.changedSrcFiles
  def outputDir = project.outputDir

  def dependsOn(tasks: Task[_]*) = new Compile(project, dependentTasks = (dependentTasks ::: tasks.toList).distinct)

}

case class TestCompile(project: Project, dependentTasks: List[Task[_]] = Nil) extends AbstractCompile(project) {

  import Environment._

  protected def execSelf = doCompilation(project.testCompiler)
  def changedSrcFiles = project.changedTestFiles
  def outputDir = project.testOutputDir

  def dependsOn(tasks: Task[_]*) = new TestCompile(project, dependentTasks = (dependentTasks ::: tasks.toList).distinct)

}
