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


case class TaskFailed(task : Task, reason : String)


abstract class CompileTask extends Task{
  // Returns 
  def compiler(proj : Project) : Global
  def outputDir(proj : Project) : File
  def changedSrcFiles(proj : Project) : Set[File]
  def exec(proj : Project, acc : List[AnyRef]) : Either[TaskFailed, AnyRef] = {
    val comp = compiler(proj)
    def listOfFiles(files : Iterable[File]) = files.mkString("\n\t", "\n\t", "")
    val reporter = comp.reporter
    if (!outputDir(proj).exists)
      outputDir(proj).mkdirs
    val modifiedSrcFiles = changedSrcFiles(proj)
    if (! modifiedSrcFiles.isEmpty) {
      Log.info("Compiling " + proj + ", " + modifiedSrcFiles.size + " modified or uncompiled files")
      val sw = new Stopwatch
      Log.debug("Changed files are " + listOfFiles(modifiedSrcFiles))
      reporter.reset
      // First compile those files who have changed
      new comp.Run() compile modifiedSrcFiles.toList.map(_.getPath)
      // Determine which source files have changed signatures

      val sourceFilesFromThisProjectWithChangedSigs: Set[File] = Set() ++ proj.updateSignatures
      val sourceFilesFromOtherProjectsWithChangedSigs = (Set[File]() /: acc.map(_.asInstanceOf[(Set[File], Set[File])]).map(_._1))(_ ++ _)
      Log.info("Files with changed sigs in " + proj + " is , " + listOfFiles(sourceFilesFromThisProjectWithChangedSigs))

      val sourceFilesWithChangedSigs = sourceFilesFromThisProjectWithChangedSigs ++ sourceFilesFromOtherProjectsWithChangedSigs

      
      val dependentFiles = proj.dependencies.dependentFiles(sourceFilesWithChangedSigs).filterNot(sourceFilesWithChangedSigs)
      Log.debug("Files dependent on those with shanged sigs" + listOfFiles(dependentFiles))
      Log.info("Compiling " + dependentFiles.size + " files dependent n those with changed sigs")
      new comp.Run() compile dependentFiles.toList.map(_.getPath)
      Log.info("time taken " + sw.toStringSeconds)
      if (reporter.hasErrors)
        Left(TaskFailed(this, "Failed to compile"))
      else {
        proj.dependencies.persist
        proj.sourceToClassFiles.persist
        Right((sourceFilesFromThisProjectWithChangedSigs, modifiedSrcFiles ++ dependentFiles))
      }
    } else {
      Log.info("Already Compiled " + proj.name)
      Right((Set[File](), Set[File]()))
    }
  }
}

case object CompileSourceTask extends CompileTask{
  def changedSrcFiles(proj : Project) = proj.changedSrcFiles
  def outputDir(proj : Project) = proj.outputDir
  def compiler(proj : Project) = proj.compiler
}

case object CompileTestsTask extends CompileTask{
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
      val parameters = "javac"::"-cp"::compilationClasspath::"-d"::javaOutputDir.getAbsolutePath::javaSrcFiles.toList.map(_.getAbsolutePath)
      Command(parameters : _*).exec match {
        case (0, _) => Right(javaFilesToCompile)
        case (_, error) => Left(TaskFailed(this, error))
      }
    }
  }
}

case object CleanTask extends Task{
  def exec(project : Project, acc : List[AnyRef]) = {
    Log.info("cleaning " + project)
    project.classFiles.foreach(_.delete)
    project.testClassFiles.foreach(_.delete)
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
    if (Runner.run(Array("-o", "-p", path)))
      Right(Unit)
    else
      Left(TaskFailed(this, "Bad test"))
  }
}

trait Task{
  def exec(project : Project, acc : List[AnyRef] = Nil) : Either[TaskFailed, AnyRef]
}

