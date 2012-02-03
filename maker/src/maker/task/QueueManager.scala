package maker.task

import maker.project.Project
import maker.utils.{Stopwatch, Log}
import akka.actor.Actor._
import akka.actor.ActorRef
import akka.routing.{CyclicIterator, Routing}
import akka.actor.{UntypedChannel, Actor}
import akka.event.EventHandler

trait TaskResult
case object TaskSuccess extends TaskResult { 
  override def toString = "OK"
}
case class TaskError(reason : String, exception : Option[Throwable]) extends TaskResult {
  override def toString = "Task error, reason: " + reason + exception.map(e => ", exception " + e.getMessage).getOrElse("")
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
case class BuildResult(res : Either[TaskFailed, AnyRef], projectAndTasks : Set[ProjectAndTask], originalProjectAndTask : ProjectAndTask) extends BuildMessage {
  import maker.graphviz.GraphVizDiGrapher._
  import maker.graphviz.GraphVizUtils._
  def stats = projectAndTasks.map(_.allStats).mkString("\n")
  
  def resultTree(pt : ProjectAndTask = originalProjectAndTask) = {
    pt.getTaskTree.map(p => (projectAndTasks.find(_ == p._1).get, p._2.map(x => projectAndTasks.find(_ == x).get)))
  }
  
  def showBuildGraph() = 
    showGraph(makeDotFromProjectAndTask(resultTree()))

  override def toString = res.toString
}

class QueueManager(projectTasks : Set[ProjectAndTask], router : ActorRef, originalProjectAndTask : ProjectAndTask) extends Actor{

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
    Log.debug("Can be processed = " + canBeProcessed)
    Log.debug("must wait " + mustWait)
    remainingProjectTasks = mustWait
    canBeProcessed.foreach{
      pt =>
        Log.debug("Launching " + pt)
        router ! ExecTaskMessage(pt, accumuland)
    }
  }
  def receive = {
    case TaskResultMessage(projectTask, Left(taskFailure)) => {
      ProjectAndTask.removeTask(projectTask)
      Log.debug("Task failed " + taskFailure)
      router.stop
      originalCaller ! BuildResult(Left(taskFailure), projectTasks, originalProjectAndTask)
    }
    case TaskResultMessage(projectTask, Right(result)) => {
      ProjectAndTask.removeTask(projectTask)
      accumuland = accumuland + (projectTask.task -> (result :: accumuland.getOrElse(projectTask.task, Nil)))
      completedProjectTasks += projectTask
      if (completedProjectTasks  == projectTasks)
        originalCaller ! BuildResult(Right(TaskSuccess), projectTasks, originalProjectAndTask)
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
  def apply(projects : List[Project], task : Task) : BuildResult = {
    val originalProjectAndTask = ProjectAndTask(projects.head, task)
    val projectTasks = {
      def recurse(moreProjectTasks : Set[ProjectAndTask], acc : Set[ProjectAndTask]) : Set[ProjectAndTask] = {
        if (moreProjectTasks.isEmpty)
          acc
        else {
          recurse(moreProjectTasks.flatMap(_.immediateDependencies), acc ++ moreProjectTasks)
        }
      }
      recurse(projects.toSet.map{proj : Project => ProjectAndTask(proj, task)}, Set[ProjectAndTask]())
    }

    Log.debug("About to do " + task + " for projects " + projects.toList.mkString(","))
    apply(projectTasks, originalProjectAndTask)
  }

  def apply(projectTasks : Set[ProjectAndTask], originalProjectAndTask : ProjectAndTask) : BuildResult = {
    val sw = Stopwatch()
    implicit val timeout = Timeout(1000000)
    def nWorkers = (Runtime.getRuntime.availableProcessors / 2) max 1


    val workers = (1 to nWorkers).map{i => actorOf(new Worker()).start}
    Log.debug("Running with " + nWorkers + " workers")
    val router = Routing.loadBalancerActor(CyclicIterator(workers)).start()
    val qm = actorOf(new QueueManager(projectTasks, router, originalProjectAndTask)).start
    
    val future = qm ? StartBuild
    val result = future.get.asInstanceOf[BuildResult]

    qm.stop
    workers.foreach(_.stop)
    router.stop
    EventHandler.shutdown()
    Log.debug("Stats: \n" + projectTasks.map(_.runStats).mkString("\n"))
    Log.info("Completed, took" + sw + ", result " + result)
    result
  }
}
