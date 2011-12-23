package maker.task

import maker.project.Project
import java.io.File
import scala.tools.nsc.Global
import maker.utils.Log
import maker.utils.Stopwatch
import org.scalatest.tools.Runner
import maker.os.Command
import maker.utils.FileUtils
import org.apache.commons.io.{FileUtils => ApacheFileUtils}
import scalaz.Scalaz._

case class TaskFailed(task : Task, reason : String)

abstract class CompileTask extends Task{
  def compiler(proj : Project) : Global
  def outputDir(proj : Project) : File
  def changedSrcFiles(proj : Project) : Set[File]
  def deletedSrcFiles(proj : Project) : Set[File]

  def exec(proj : Project, acc : List[AnyRef]) : Either[TaskFailed, AnyRef] = {
    def listOfFiles(files : Iterable[File]) = files.mkString("\n\t", "\n\t", "")
    val comp = compiler(proj)
    comp.settings.classpath.value = proj.compilationClasspath
    val reporter = comp.reporter
    outputDir(proj).mkdirs
    
    val modifiedSrcFiles = changedSrcFiles(proj)
    val deletedSrcFiles_ = deletedSrcFiles(proj)
    Log.info("Project " + proj)
    deletedSrcFiles_.foreach(println)

    if (modifiedSrcFiles.isEmpty && deletedSrcFiles_.isEmpty) {
      Log.info("Already Compiled " + proj.name)
      Right((Set[File](), Set[File]()))
    } else {
      proj.sourceToClassFiles.classFilesFor(deletedSrcFiles_) |> {
        classFiles =>
          Log.info("Deleting " + classFiles.size + " class files")
          classFiles.foreach(_.delete)
      }
      Log.info("Compiling " + proj + ", " + modifiedSrcFiles.size + " modified or uncompiled files")
      val sw = new Stopwatch
      
      Log.debug("Changed files are " + listOfFiles(modifiedSrcFiles))
      reporter.reset
      // First compile those files who have changed
      new comp.Run() compile modifiedSrcFiles.toList.map(_.getPath)

      // Determine which source files have changed signatures
      val sourceFilesFromThisProjectWithChangedSigs: Set[File] = Set() ++ proj.updateSignatures
      val sourceFilesFromOtherProjectsWithChangedSigs = (Set[File]() /: acc.map(_.asInstanceOf[(Set[File], Set[File])]).map(_._1))(_ ++ _)
      Log.debug("Files with changed sigs in " + proj + " is , " + listOfFiles(sourceFilesFromThisProjectWithChangedSigs))

      val dependentFiles = (sourceFilesFromThisProjectWithChangedSigs ++ sourceFilesFromOtherProjectsWithChangedSigs ++ deletedSrcFiles_) |> {
        filesWhoseDependentsMustRecompile => 
          val dependentFiles = proj.dependencies.dependentFiles(filesWhoseDependentsMustRecompile).filterNot(filesWhoseDependentsMustRecompile)
          Log.debug("Files dependent on those with shanged sigs" + listOfFiles(dependentFiles))
          Log.info("Compiling " + dependentFiles.size + " dependent files")
          new comp.Run() compile dependentFiles.toList.map(_.getPath)
          Log.info("time taken " + sw.toStringSeconds)
          dependentFiles
      }

      if (reporter.hasErrors)
        Left(TaskFailed(this, "Failed to compile"))
      else {
        proj.dependencies.persist
        proj.sourceToClassFiles.persist
        Right((sourceFilesFromThisProjectWithChangedSigs, modifiedSrcFiles ++ dependentFiles))
      }
    }
  }
}

case object CompileSourceTask extends CompileTask{
  def deletedSrcFiles(proj : Project) = proj.deletedSrcFiles
  def changedSrcFiles(proj : Project) = proj.changedSrcFiles
  def outputDir(proj : Project) = proj.outputDir
  def compiler(proj : Project) = proj.compiler
}

case object CompileTestsTask extends CompileTask{
  def deletedSrcFiles(proj : Project) = proj.deletedTestFiles
  def changedSrcFiles(proj : Project) = proj.changedTestFiles
  def outputDir(proj : Project) = proj.testOutputDir
  def compiler(proj : Project) = proj.testCompiler
}

case object CompileJavaSourceTask extends Task{

  def exec(project : Project, acc : List[AnyRef]) = {
    import project._
    javaOutputDir.mkdirs
    val javaFilesToCompile = changedJavaFiles
    if (javaFilesToCompile.isEmpty)
      Right(Set())
    else {
      Log.info("Compiling " + javaFilesToCompile.size + " java files")
      val parameters = "javac"::"-cp"::compilationClasspath::"-d"::javaOutputDir.getAbsolutePath::javaSrcFiles.toList.map(_.getAbsolutePath)
      Command(parameters : _*).exec match {
        case (0, _) => Right(javaFilesToCompile)
        case (_, error) => Left(TaskFailed(this, error))
      }
    }
  }
}

case object UpdateExternalDependencies extends Task{

  def exec(project : Project, acc : List[AnyRef]) = {
    import project._
    managedLibDir.mkdirs
    Log.info("Updating " + name)
    def command(isSources : Boolean) : Command = {
      val parameters = "java"::"-jar"::"lib/ivy/ivy.jar"::"-settings"::ivySettingsFile.getPath::"-ivy"::ivyFile.getPath::"-retrieve"::nil :::
        (if (isSources)
          (managedLibDir.getPath + "/[artifact]-[revision]-source.[ext]")::"-types"::"source"::nil
        else
          (managedLibDir.getPath + "/[artifact]-[revision].[ext]")::"-sync"::"-types"::"jar"::nil)
      val cmd = Command(parameters : _*)
      Log.debug(cmd)
      println(cmd)
      cmd
    }
    if (ivyFile.exists){
      command(false).exec match {
        case (0, _) => command(true).exec match {
          case (0, _) => Right("OK")
          case (_, error) => Left(TaskFailed(this, error))
        }
        case (_, error) => Left(TaskFailed(this, error))
      }
    } else {
      Log.info("Nothing to update")
      Right("OK")
    }
  }

}

case object CleanTask extends Task{
  def exec(project : Project, acc : List[AnyRef]) = {
    Log.info("cleaning " + project)
    project.classFiles.foreach(_.delete)
    project.testClassFiles.foreach(_.delete)
    project.javaClassFiles.foreach(_.delete)
    project.outputJar.delete
    Right(Unit)
  }
}

case object PackageTask extends Task{
  import maker.os.Environment._
  def exec(project : Project, acc : List[AnyRef]) = {
    import project._
    if (!packageDir.exists)
      packageDir.mkdirs
    FileUtils.withTempDir({
      dir : File =>
        allDependencies().foreach{
          depProj =>
            ApacheFileUtils.copyDirectory(depProj.outputDir, dir)
        }
      val cmd = Command(jar, "cf", project.outputJar.getAbsolutePath, "-C", dir.getAbsolutePath, ".")
      cmd.exec match {
        case (0, _) => Right(Unit)
        case (errNo, errMessage) => Left(TaskFailed(this, errMessage))
      }
    }, false)

  }
}
case object RuntUnitTestsTask extends Task{

  def exec(project : Project, acc : List[AnyRef]) = {
    Log.info("Testing " + project)
    val path = project.testOutputDir.getAbsolutePath + " " + project.outputDir.getAbsolutePath
    if (Runner.run(Array("-c", "-o", "-p", path)))
      Right(Unit)
    else
      Left(TaskFailed(this, "Bad test"))
  }
}

trait Task{
  def exec(project : Project, acc : List[AnyRef] = Nil) : Either[TaskFailed, AnyRef]
}
