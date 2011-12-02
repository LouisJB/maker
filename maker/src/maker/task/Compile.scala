package maker.task

import maker.project.Project
import maker.os.Environment
import maker.utils.Log
import java.io.File
import scala.tools.nsc.reporters.AbstractReporter
import scala.tools.nsc.Global
import maker.utils.Stopwatch
import maker.os.Command

abstract class AbstractCompile(project : Project) extends Task[Set[File]]{
  val lock = new Object
  def outputDir : File
  def changedSrcFiles() : Set[File]

  protected def doCompilation(compiler : Global): Either[TaskFailed, Set[File]] = {
    def listOfFiles(files : Iterable[File]) = files.mkString("\n\t", "\n\t", "")
    val reporter = compiler.reporter
    if (!outputDir.exists)
      outputDir.mkdirs
    val modifiedSrcFiles = changedSrcFiles()
    if (! modifiedSrcFiles.isEmpty) {
      Log.info("Compiling " + project + ", " + modifiedSrcFiles.size + " modified or uncompiled files")
      val sw = new Stopwatch
      Log.debug("Changed files are " + listOfFiles(modifiedSrcFiles))
      reporter.reset
      // First compile those files who have changed
      new compiler.Run() compile modifiedSrcFiles.toList.map(_.getPath)
      // Determine which source files have changed signatures

      val sourceFilesWithChangedSigs: Set[File] = Set() ++ project.updateSignatures
      Log.info("Files with changed sigs " + listOfFiles(sourceFilesWithChangedSigs))
      val dependentFiles = project.dependencies.dependentFiles(sourceFilesWithChangedSigs).filterNot(sourceFilesWithChangedSigs)
      Log.debug("Files dependent on those with shanged sigs" + listOfFiles(dependentFiles))
      Log.info("Compiling " + dependentFiles.size + " files dependent n those with changed sigs")
      new compiler.Run() compile dependentFiles.toList.map(_.getPath)
      Log.info("time taken " + sw.toStringSeconds)
      if (reporter.hasErrors)
        Left(TaskFailed(this, "Failed to compile"))
      else {
        project.dependencies.persist
        Right(modifiedSrcFiles ++ dependentFiles)
      }
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

case class JavaCompile(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[Set[File]]{
  val lock = new Object
  def dependsOn(tasks: Task[_]*) = new JavaCompile(project, dependentTasks = (dependentTasks ::: tasks.toList).distinct)
  protected def execSelf = {

    import project._
    javaOutputDir.mkdirs
    val javaFilesToCompile = changedJavaFiles
    if (javaFilesToCompile.isEmpty)
      Right(Set())
    else {
      val parameters = "javac"::"-cp"::compilationClasspath::"-d"::javaOutputDir.getAbsolutePath::javaSrcFiles.toList.map(_.getAbsolutePath)
      Command(parameters : _*).exec match {
        case (0, _) => Right(javaFilesToCompile)
        case (_, error) => Left(TaskFailed(this, error))
      }
    }
  }
}
