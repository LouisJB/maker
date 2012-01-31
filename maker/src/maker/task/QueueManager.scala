package maker.task

import maker.project.Project
import akka.actor.Actor._
import akka.actor.ActorRef
import akka.routing.{CyclicIterator, Routing}
import akka.actor.{UntypedChannel, Actor}
import maker.utils.{Stopwatch, Log}
import akka.event.EventHandler

case class TaskError(reason : String, exception : Option[Throwable]) {
  override def toString = "Task error, reason: " + reason + exception.map(e => ", exception " + e.getMessage).getOrElse("")
}

object ProjectAndTask{
  val lock = new Object
  var runningTasks = List[ProjectAndTask]()
  private def reportTasks{
    lock.synchronized{
      println("There are " + runningTasks.size + " task(s) currently running")
      println(runningTasks.mkString("\t", "\n\t", "\n"))
    }
  }
  def addTask(pt : ProjectAndTask){
    lock.synchronized{
      println("Adding task " + pt)
      runningTasks = pt :: runningTasks
      reportTasks
    }
  }
  def removeTask(pt : ProjectAndTask){
    lock.synchronized{
      println("Removing task " + pt)
      runningTasks = runningTasks.filterNot(_ == pt)
      reportTasks
    }
  }
}
case class ProjectAndTask(project : Project, task : Task) {

  private var lastRunTimeMs_ = 0L
  private var lastError_ : Option[TaskError] = None

  def lastRunTimeMs = lastRunTimeMs_
  def lastError = lastError_
  val immediateDependencies : Set[ProjectAndTask] = {
    project.acrossProjectImmediateDependencies(task)
  }

  def exec(acc : Map[Task, List[AnyRef]]) = {
    ProjectAndTask.addTask(this)
    Log.debug("Executing " + this)
    val sw = new Stopwatch()
    val taskResult = try {
      task.exec(project, acc.getOrElse(task, Nil)) match {
        case res @ Left(err) =>
          lastError_ = Some(TaskError(err.reason, None))
          res
        case res => res
      }
    } catch {
      case e =>
        Log.info("Error occured when executing " + this)
        lastError_ = Some(TaskError("Internal Exception", Some(e)))
        e.printStackTrace
        Left(TaskFailed(this, e.getMessage))
    }
    val totalTime = sw.ms()
    lastRunTimeMs_ = totalTime
    Log.info("%s completed in %dms".format(this, totalTime))
    taskResult
  }

  override def toString = "Task[" + project.name + ":" + task + "]"

  def runStats =
    toString + " took " + lastRunTimeMs + "ms"
  
  def allStats = "%s took %d, status %s".format(
    toString, lastRunTimeMs, lastError.map(_.toString).getOrElse("OK"))
}

sealed trait BuildMessage
case class ExecTaskMessage(projectTask : ProjectAndTask, acc : Map[Task, List[AnyRef]]) extends BuildMessage
case class TaskResultMessage(projectTask : ProjectAndTask, result : Either[TaskFailed, AnyRef]) extends BuildMessage
case object StartBuild extends BuildMessage
class Worker() extends Actor{
  def receive = {
    case ExecTaskMessage(projectTask : ProjectAndTask, acc : Map[Task, List[AnyRef]]) => self reply TaskResultMessage(projectTask, projectTask.exec(acc))
  }
}
case class BuildResult(projectAndTasks : Set[ProjectAndTask], res : Either[TaskFailed, AnyRef]) extends BuildMessage {
  def stats = projectAndTasks.map(_.allStats).mkString("\n")
  override def toString = res.toString
}

class QueueManager(projectTasks : Set[ProjectAndTask], router : ActorRef) extends Actor{

  var accumuland : Map[Task, List[AnyRef]] = Map[Task, List[AnyRef]]()
  var remainingProjectTasks = projectTasks
  var completedProjectTasks : Set[ProjectAndTask] = Set()
  var originalCaller : UntypedChannel = _
  private def execNextLevel{
    Log.debug("Remaining tasks are " + remainingProjectTasks)
    Log.debug("Completed tasks are " + completedProjectTasks)
    val (canBeProcessed, mustWait) = remainingProjectTasks.partition(
      _.immediateDependencies.filterNot(completedProjectTasks).filter(projectTasks).isEmpty
    )
    Log.info("Can be processed = " + canBeProcessed)
    Log.debug("must wait " + mustWait)
    remainingProjectTasks = mustWait
    canBeProcessed.foreach{
      pt =>
        Log.info("Launching " + pt)
        router ! ExecTaskMessage(pt, accumuland)
    }
  }
  def receive = {
    case TaskResultMessage(projectTask, Left(taskFailure)) => {
      ProjectAndTask.removeTask(projectTask)
      Log.debug("Task failed " + taskFailure)
      router.stop
      originalCaller ! BuildResult(projectTasks, Left(taskFailure))
    }
    case TaskResultMessage(projectTask, Right(result)) => {
      ProjectAndTask.removeTask(projectTask)
      accumuland = accumuland + (projectTask.task -> (result :: accumuland.getOrElse(projectTask.task, Nil)))
      completedProjectTasks += projectTask
      if (completedProjectTasks  == projectTasks)
        originalCaller ! BuildResult(projectTasks, Right("OK"))
      else {
        remainingProjectTasks = remainingProjectTasks.filterNot(_ == projectTask)
        if (router.isRunning)
          execNextLevel
      }
    }
    case StartBuild => {
      originalCaller = self.channel
      execNextLevel
    }
  }
}

object QueueManager{
  def apply(projects : Set[Project], task : Task) : BuildResult = {
    val projectTasks = {
      def recurse(moreProjectTasks : Set[ProjectAndTask], acc : Set[ProjectAndTask]) : Set[ProjectAndTask] = {
        if (moreProjectTasks.isEmpty)
          acc
        else {
          recurse(moreProjectTasks.flatMap(_.immediateDependencies), acc ++ moreProjectTasks)
        }
      }
      recurse(projects.map(ProjectAndTask(_, task)), Set[ProjectAndTask]())
    }

    Log.info("About to do " + task + " for projects " + projects.toList.mkString(","))
    apply(projectTasks)
  }

  def apply(projectTasks : Set[ProjectAndTask]) : BuildResult = {
    val sw = Stopwatch()
    implicit val timeout = Timeout(1000000)
    def nWorkers = (Runtime.getRuntime.availableProcessors / 2) max 1


    val workers = (1 to nWorkers).map{i => actorOf(new Worker()).start}
    Log.info("Running with " + nWorkers + " workers")
    val router = Routing.loadBalancerActor(CyclicIterator(workers)).start()
    val qm = actorOf(new QueueManager(projectTasks, router)).start 
    
    val future = qm ? StartBuild
    val result = future.get.asInstanceOf[BuildResult]

    qm.stop
    workers.foreach(_.stop)
    router.stop
    EventHandler.shutdown()
    Log.info("Stats: \n" + projectTasks.map(_.runStats).mkString("\n"))
    Log.info("Completed, took" + sw)
    result
  }
}
