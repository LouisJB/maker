package maker.task

import maker.project.Project
import maker.utils.{Stopwatch, Log}
import akka.actor.Actor._
import akka.actor.ActorRef
import akka.actor.Actor
import java.io.File
import akka.util.Timeout
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.SmallestMailboxRouter
import akka.dispatch.Await
import akka.util.Duration
import akka.pattern.ask
import akka.util.duration._

class TaskManager(projectTasks : Set[ProjectAndTask], router : ActorRef,
                   originalProjectAndTask : ProjectAndTask,
                   parameters : Map[String, String] = Map()) extends Actor {

  var accumuland : Map[Task, List[AnyRef]] = Map[Task, List[AnyRef]]()
  var remainingProjectTasks = projectTasks
  var completedProjectTasks : Set[ProjectAndTask] = Set()
  var originalCaller : ActorRef = _
  var roundNo = 0
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
        pt.roundNo = roundNo
        Log.debug("Launching " + pt)
        router ! ExecTaskMessage(pt, accumuland, parameters)
    }
    roundNo += 1
  }
  def receive = {
    case TaskResultMessage(projectTask, Left(taskFailure)) => {
      ProjectAndTask.removeTask(projectTask)
      Log.debug("Task failed " + taskFailure)
      //router.stop
      originalCaller ! BuildResult(Left(taskFailure), projectTasks, originalProjectAndTask)
    }
    case TaskResultMessage(projectTask, Right(result)) => {
      ProjectAndTask.removeTask(projectTask)
      accumuland = accumuland + (projectTask.task -> (result :: accumuland.getOrElse(projectTask.task, Nil)))
      completedProjectTasks += projectTask
      if (completedProjectTasks == projectTasks)
        originalCaller ! BuildResult(Right(TaskSuccess), projectTasks, originalProjectAndTask)
      else {
        remainingProjectTasks = remainingProjectTasks.filterNot(_ == projectTask)
        if (! router.isTerminated)
          execNextLevel
      }
    }
    case StartBuild => {
      originalCaller = sender
      execNextLevel
    }
  }
}

object TaskManager{
  def apply(projects : List[Project], task : Task, parameters : Map[String, String] = Map()) : BuildResult[AnyRef] = {
    val sw = new Stopwatch()
    val originalProjectAndTask = ProjectAndTask(projects.head, task)
    val projectTasks = {
      def recurse(moreProjectTasks : Set[ProjectAndTask], acc : Set[ProjectAndTask]) : Set[ProjectAndTask] = {
        if (moreProjectTasks.isEmpty) acc
        else
          recurse(moreProjectTasks.flatMap(_.immediateDependencies), acc ++ moreProjectTasks)
      }
      recurse(projects.toSet.map{proj : Project => ProjectAndTask(proj, task)}, Set[ProjectAndTask]())
    }
    Log.debug("took %s to compute %d project tasks".format(sw, projectTasks.size))
    Log.debug("About to do " + task + " for projects " + projects.toList.mkString(","))
    projects.head.props.CompilationOutputStream.emptyVimErrorFile
    apply(projectTasks, originalProjectAndTask, parameters)
  }

  def apply(projectTasks : Set[ProjectAndTask], originalProjectAndTask : ProjectAndTask, parameters : Map[String, String]) : BuildResult[AnyRef] = {
    val system = ActorSystem("QueueManager")
    val sw = Stopwatch()
    implicit val timeout = Timeout(1000000)
    def nWorkers = (Runtime.getRuntime.availableProcessors / 2) max 1

    Log.debug("Running with " + nWorkers + " workers")
    val router = system.actorOf(Props[Worker].withRouter(SmallestMailboxRouter(nWorkers)))
    val qm = system.actorOf(Props(new TaskManager(projectTasks, router, originalProjectAndTask, parameters)))
    val future = qm ? StartBuild
    val result = Await.result(future, Duration.Inf).asInstanceOf[BuildResult[AnyRef]]

    system.shutdown()
    Log.debug("Stats: \n" + projectTasks.map(_.runStats).mkString("\n"))
    Log.info("Completed " + originalProjectAndTask + ", took" + sw + ", result " + result)
    result
  }
}
