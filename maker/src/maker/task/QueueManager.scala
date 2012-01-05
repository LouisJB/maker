package maker.task

import maker.project.Project
import akka.actor.Actor._
import akka.routing.{CyclicIterator, Routing}
import akka.actor.{UntypedChannel, PoisonPill, Actor}
import maker.utils.Log

case class ProjectAndTask(project : Project, task : Task){
  val properDependencies : Set[ProjectAndTask] = project.taskDependencies(task).map(ProjectAndTask(project, _)) ++ project.dependentProjects.flatMap(ProjectAndTask(_, task).allDependencies)
  def allDependencies = properDependencies + this
  def exec(acc : Map[Task, List[AnyRef]]) = {
    Log.debug("Executing task " + task + ", for project " + project.name)
    task.exec(project, acc.getOrElse(task, Nil))
  }
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
case class BuildResult(res : Either[TaskFailed, AnyRef]) extends BuildMessage

class QueueManager(projectTasks : Set[ProjectAndTask], nWorkers : Int) extends Actor{

  var accumuland : Map[Task, List[AnyRef]] = Map[Task, List[AnyRef]]()
  val workers = Vector.fill(nWorkers)(actorOf(new Worker()).start)
  workers.foreach(_.start())
  val router = Routing.loadBalancerActor(CyclicIterator(workers)).start()
  var remainingProjectTasks = projectTasks
  var completedProjectTasks : Set[ProjectAndTask] = Set()
  var originalCaller : UntypedChannel = _
  private def execNextLevel{
    Log.debug("Remaining tasks are " + remainingProjectTasks)
    val (canBeProcessed, mustWait) = remainingProjectTasks.partition(
      _.properDependencies.filterNot(completedProjectTasks).isEmpty
    )
    Log.debug("Can be processed = " + canBeProcessed)
    Log.debug("must wait " + mustWait)
    remainingProjectTasks = mustWait
    canBeProcessed.foreach(router ! ExecTaskMessage(_, accumuland))
  }
  def receive = {
    case TaskResultMessage(_, Left(taskFailure)) => {
      router ! PoisonPill
      originalCaller ! BuildResult(Left(taskFailure))
    }
    case TaskResultMessage(projectTask, Right(result)) => {
      accumuland = accumuland + (projectTask.task -> (result :: accumuland.getOrElse(projectTask.task, Nil)))
      completedProjectTasks += projectTask
      if (completedProjectTasks  == projectTasks)
        originalCaller ! BuildResult(Right("OK"))
      else {
        remainingProjectTasks = remainingProjectTasks.filterNot(_ == projectTask)
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
  def apply(projects : Set[Project], task : Task) = {
    Log.info("About to do " + task + " for projects " + projects.toList.mkString(","))
    val projectTasks = projects.flatMap(ProjectAndTask(_, task).allDependencies)
    implicit val timeout = Timeout(1000000)
    val future = actorOf(new QueueManager(projectTasks, 2)).start ? StartBuild
    future.get.asInstanceOf[BuildResult].res
  }
}
