package maker.task.tasks

import maker.project.Project
import maker.task.{TaskFailed, Task, ProjectAndTask}
import maker.utils.Log
import maker.utils.os.Command
import java.io.File
import tools.nsc.Global
import scalaz.Scalaz._
import maker.utils.os.CommandOutputHandler
import maker.Maker

abstract class CompileTask extends Task{
  def compiler(proj : Project) : Global
  def outputDir(proj : Project) : File
  def changedSrcFiles(proj : Project) : Set[File]
  def deletedSrcFiles(proj : Project) : Set[File]
  def taskName : String

  def exec(proj : Project, acc : List[AnyRef], parameters : Map[String, String] = Map()) : Either[TaskFailed, AnyRef] = {
    def listOfFiles(files : Iterable[File]) = files.mkString("\n\t", "\n\t", "")
    def info(msg : String) = Log.info("\t" + taskName + proj + ": " + msg)
    def debug(msg : String) = Log.debug("\t" + taskName + proj + ": " + msg)
    val comp = compiler(proj)
    comp.settings.classpath.value = proj.compilationClasspath
    val reporter = comp.reporter
    outputDir(proj).mkdirs

    val modifiedSrcFiles = changedSrcFiles(proj)
    val deletedSrcFiles_ = deletedSrcFiles(proj)

    if (modifiedSrcFiles.isEmpty && deletedSrcFiles_.isEmpty) {
      Right((Set[File](), Set[File]()))
    } else {
      if (Maker.verboseTaskOutput && ! proj.suppressTaskOutput)
        Log.info("Compiling " + proj)
      else
        print(".")

      deleteClassFilesGeneratedBy(deletedSrcFiles_)
      debug("Compiling, " + modifiedSrcFiles.size + " modified or uncompiled files")

      debug("Changed files are " + listOfFiles(modifiedSrcFiles))
      reporter.reset

      def deleteClassFilesGeneratedBy(srcFiles : Set[File]){
        proj.state.classFilesGeneratedBy(srcFiles).filter(_.exists) |> {
          classFiles =>
            if (classFiles.size > 0){
              debug(
                "Deleting " + classFiles.size + " class files" + 
                classFiles.toList.mkString("\n\t", "\n\t", "\n")
              )
            } 
            classFiles.foreach(_.delete)
        }
      }
      def compileSourceFiles(srcFiles : Set[File]){
        deleteClassFilesGeneratedBy(srcFiles)
        new comp.Run() compile srcFiles.toList.map(_.getPath)
      }

      // First compile those files who have changed
      compileSourceFiles(modifiedSrcFiles)

      // Determine which source files have changed signatures
      val sourceFilesFromThisProjectWithChangedSigs: Set[File] = Set() ++ proj.state.updateSignatures
      val sourceFilesFromOtherProjectsWithChangedSigs = (Set[File]() /: acc.map(_.asInstanceOf[(Set[File], Set[File])]).map(_._1))(_ ++ _)
      debug("Files with changed sigs is , " + listOfFiles(sourceFilesFromThisProjectWithChangedSigs))

      val dependentFiles = (sourceFilesFromThisProjectWithChangedSigs ++ sourceFilesFromOtherProjectsWithChangedSigs ++ deletedSrcFiles_) |> {
        filesWhoseDependentsMustRecompile =>
          val dependentFiles = proj.state.fileDependencies.sourceChildDependencies(filesWhoseDependentsMustRecompile).filterNot(filesWhoseDependentsMustRecompile)
          debug("Files dependent on those with changed sigs" + listOfFiles(dependentFiles))
          debug("Compiling " + dependentFiles.size + " dependent files")
          compileSourceFiles(dependentFiles)
          //new comp.Run() compile dependentFiles.toList.map(_.getPath)
          dependentFiles
      }

      if (reporter.hasErrors)
        Left(TaskFailed(ProjectAndTask(proj, this), "Failed to compile"))
      else {
        proj.state.fileDependencies.persist
        CompileJavaSourceTask.exec(proj, acc, parameters) match {
          case Right(javaFilesToCompile) => Right((sourceFilesFromThisProjectWithChangedSigs, modifiedSrcFiles ++ dependentFiles))
          case err @ Left(TaskFailed(ProjectAndTask(project, _), error)) => err
        }
      }
    }
  }
}

case object CompileSourceTask extends CompileTask {
  def deletedSrcFiles(proj: Project) = proj.state.deletedSrcFiles
  def changedSrcFiles(proj: Project) = proj.state.changedSrcFiles
  def outputDir(proj: Project) = proj.outputDir
  def compiler(proj: Project) = proj.compilers.compiler
  def taskName = "Compile source"
}

case object CompileTestsTask extends CompileTask{
  def deletedSrcFiles(proj : Project) = proj.state.deletedTestFiles
  def changedSrcFiles(proj : Project) = proj.state.changedTestFiles
  def outputDir(proj : Project) = proj.testOutputDir
  def compiler(proj : Project) = proj.compilers.testCompiler
  def taskName = "Compile test"
}

case object CompileJavaSourceTask extends Task {

  def exec(project : Project, acc : List[AnyRef], parameters : Map[String, String] = Map()) = {
    import project._
    javaOutputDir.mkdirs
    val javaFilesToCompile = state.changedJavaFiles
    if (javaFilesToCompile.isEmpty)
      Right(Set())
    else {
      Log.debug("Compiling " + javaFilesToCompile.size + " java files")
      val javac = project.props.JavaHome().getAbsolutePath + "/bin/javac"
      val parameters = javac :: "-cp" :: compilationClasspath :: "-d" :: javaOutputDir.getAbsolutePath :: javaSrcFiles.toList.map(_.getAbsolutePath)
      val cmd = Command(CommandOutputHandler().withSavedOutput, None, parameters : _*)
      cmd.exec() match {
        case 0 => Right(javaFilesToCompile)
        case error => Left(TaskFailed(ProjectAndTask(project, this), "Error " + error + ", output " + cmd.savedOutput))
      }
    }
  }
}
