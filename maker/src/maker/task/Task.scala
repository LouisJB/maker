package maker.task
import maker.project.Project
import java.io.File
import scala.tools.nsc.Global
import maker.utils.Log
import maker.utils.Stopwatch
import akka.actor._
import akka.routing.Routing
import akka.routing.CyclicIterator
import Actor._
import Routing._
import org.scalatest.tools.Runner
import maker.os.Command
import maker.utils.FileUtils
import org.apache.commons.io.{FileUtils => ApacheFileUtils}

case class TaskFailed(task : Task[_], reason : String)

trait Task[T]{
  def lock : Object
  def exec : Either[TaskFailed, T] = {
    dependentTasks.foreach(
      _.exec match {
        case Left(TaskFailed(task, reason)) => return Left(TaskFailed(task, reason))
        case _ =>
      }
    )

    lock.synchronized{
      try {
        execSelf
      } catch {
        case ex =>
          Left(TaskFailed(this, ex.getMessage))
          ex.printStackTrace()
          throw ex
      }
    }
  }
  protected def execSelf : Either[TaskFailed, T]
  def dependentTasks : Seq[Task[_]]
  def dependsOn(tasks : Task[_]*) : Task[T]
}

case class TaskFailed2(task : SingleProjectTask, reason : String)


abstract class CompileTask extends SingleProjectTask{
  // Returns 
  def compiler(proj : Project) : Global
  def outputDir(proj : Project) : File
  def changedSrcFiles(proj : Project) : Set[File]
  def execSelf(proj : Project, acc : List[AnyRef]) : Either[TaskFailed2, AnyRef] = {
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
        Left(TaskFailed2(this, "Failed to compile"))
      else {
        proj.dependencies.persist
        Right((sourceFilesFromThisProjectWithChangedSigs, modifiedSrcFiles ++ dependentFiles))
      }
    } else {
      Log.info("Already Compiled " + proj.name)
      Right((Set[File](), Set[File]()))
    }
  }
}

case class CompileSourceTask(dependentTasks : List[SingleProjectTask] = Nil) extends CompileTask{
  def changedSrcFiles(proj : Project) = proj.changedSrcFiles
  def outputDir(proj : Project) = proj.outputDir
  def compiler(proj : Project) = proj.compiler
}

case class CompileTestsTask(dependentTasks : List[SingleProjectTask] = List(CompileSourceTask())) extends CompileTask{
  def changedSrcFiles(proj : Project) = proj.changedTestFiles
  def outputDir(proj : Project) = proj.testOutputDir
  def compiler(proj : Project) = proj.testCompiler
}

case class CompileJavaSourceTask(dependentTasks : List[SingleProjectTask] = Nil) extends SingleProjectTask{

  def execSelf(project : Project, acc : List[AnyRef]) = {
    import project._
    javaOutputDir.mkdirs
    val javaFilesToCompile = changedJavaFiles
    if (javaFilesToCompile.isEmpty)
      Right(Set())
    else {
      val parameters = "javac"::"-cp"::compilationClasspath::"-d"::javaOutputDir.getAbsolutePath::javaSrcFiles.toList.map(_.getAbsolutePath)
      Command(parameters : _*).exec match {
        case (0, _) => Right(javaFilesToCompile)
        case (_, error) => Left(TaskFailed2(this, error))
      }
    }
  }
}

case class CleanTask(dependentTasks : List[SingleProjectTask] = Nil) extends SingleProjectTask{
  def execSelf(project : Project, acc : List[AnyRef]) = {
    Log.info("cleaning " + project)
    project.classFiles.foreach(_.delete)
    project.testClassFiles.foreach(_.delete)
    project.outputJar.delete
    Right(Unit)
  }
}

case class PackageTask(dependentTasks : List[SingleProjectTask] = List(CompileSourceTask())) extends SingleProjectTask{
  import maker.os.Environment._
  def execSelf(project : Project, acc : List[AnyRef]) = {
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
        case (errNo, errMessage) => Left(TaskFailed2(this, errMessage))
      }
    }, false)

  }
}
case class TestTask(dependentTasks : List[SingleProjectTask] = List(CompileTestsTask())) extends SingleProjectTask{

  def dependsOn(tasks : SingleProjectTask*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)
  
  def execSelf(project : Project, acc : List[AnyRef]) = {
    Log.info("Testing " + project)
    //if (Runner.run(Array("-o", "-p", "\"" + project.testOutputDir.getAbsolutePath + "\"")))
    val path = project.testOutputDir.getAbsolutePath + " " + project.outputDir.getAbsolutePath 
    println(path)
    if (Runner.run(Array("-o", "-p", path)))
      Right(Unit)
    else
      Left(TaskFailed2(this, "Bad test"))
  }
}

trait SingleProjectTask{
  def dependentTasks : Seq[SingleProjectTask]
  def exec(project : Project, acc : List[AnyRef] = Nil) : Either[TaskFailed2, AnyRef] = {
    dependentTasks.foreach{
      task => 
        task.exec(project) match {
          case Left(TaskFailed2(task, reason)) => return Left(TaskFailed2(task, reason))
          case _ =>
        }
    }
    execSelf(project, acc)
  }
  def execSelf(project : Project, acc : List[AnyRef]) : Either[TaskFailed2, AnyRef]
}
sealed trait BuildMessage
case class ExecTaskMessage(proj : Project, acc : List[AnyRef]) extends BuildMessage
case class TaskResultMessage(proj : Project, result : Either[TaskFailed2, AnyRef]) extends BuildMessage
case object StartBuild extends BuildMessage
class Worker(task : SingleProjectTask) extends Actor{
  def receive = {
    case ExecTaskMessage(proj : Project, acc : List[AnyRef]) => self reply TaskResultMessage(proj, task.exec(proj, acc))
  }
}
case class BuildResult(res : Either[TaskFailed2, AnyRef]) extends BuildMessage

class QueueManager(projects : Set[Project], nWorkers : Int, val task : SingleProjectTask) extends Actor{

  var accumuland : List[AnyRef] = Nil
  val workers = Vector.fill(nWorkers)(actorOf(new Worker(task)).start)
  val router = Routing.loadBalancerActor(CyclicIterator(workers)).start()
  var remainingProjects = projects
  var completedProjects : Set[Project] = Set()
  var originalCaller : UntypedChannel = _
  private def execNextLevel{
    val (canBeProcessed, mustWait) = remainingProjects.partition(
      _.dependentProjects.filterNot(remainingProjects).isEmpty
    )
    remainingProjects = mustWait
    canBeProcessed.foreach(router ! ExecTaskMessage(_, accumuland))
  }
  def receive = {
    case TaskResultMessage(_, Left(taskFailure)) => {
      router ! PoisonPill
      originalCaller ! BuildResult(Left(taskFailure))
    }
    case TaskResultMessage(proj, Right(result)) => {
      accumuland = result :: accumuland
      completedProjects += proj
      if (completedProjects  == projects)
        originalCaller ! BuildResult(Right("OK"))
      else {
        remainingProjects = remainingProjects.filterNot(_ == proj)
        execNextLevel
      }
    }
    case StartBuild => {
      originalCaller = self.channel
      execNextLevel
    }
  }
}

object Build{
  def apply(projects : Set[Project], task : SingleProjectTask) = {
    implicit val timeout = Timeout(100000)
    val future = actorOf(new QueueManager(projects, 2, task)).start ? StartBuild
    future.get.asInstanceOf[BuildResult].res
  }
}
