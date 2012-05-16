package maker.task

import maker.project.Project
import maker.utils.{Stopwatch, Log}
import akka.actor.ActorRef
import akka.actor.Actor
import akka.util.Timeout
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.SmallestMailboxRouter
import akka.dispatch.Await
import akka.util.Duration
import akka.pattern.ask
import java.util.concurrent.TimeUnit._
import java.util.concurrent.TimeoutException

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
    Thread.currentThread.setContextClassLoader(TaskManager.getClass.getClassLoader)
    val system = ActorSystem("QueueManager")
    val sw = Stopwatch()
    implicit val timeout = Timeout(1000000)
    def nWorkers = (Runtime.getRuntime.availableProcessors / 2) max 1

    Log.debug("Running with " + nWorkers + " workers")
    val router = system.actorOf(Props[Worker].withRouter(SmallestMailboxRouter(nWorkers)))
    val qm = system.actorOf(Props(new TaskManager(projectTasks, router, originalProjectAndTask, parameters)))
    
    // wait for build to complete or user termination
    def startAndMonitor() : BuildResult[AnyRef] = {
      Log.info("Starling build, press %s to terminate".format(Task.termSym))
      val future = qm ? StartBuild
      while(true) {
        try {
          Await.result(future, Duration(1, SECONDS)) match {
            case v @ BuildResult(_, _, _) => return v.asInstanceOf[BuildResult[AnyRef]]
            case _ =>
          }
        }
        catch {
          case _ : TimeoutException => {
            if (System.in.available > 0 && System.in.read == Task.termChar) {
              Log.info("Terminating build...")
              system.shutdown()
              return BuildResult(Left(TaskFailed(originalProjectAndTask, "User terminated")) : Either[TaskFailed, AnyRef], projectTasks, originalProjectAndTask)
            }
          }
        }
      }
      throw new Exception("need to refactor this!")
    }
    val result : BuildResult[AnyRef] = startAndMonitor()

    system.shutdown()

    Log.debug("Stats: \n" + projectTasks.map(_.runStats).mkString("\n"))
    if (! originalProjectAndTask.project.suppressTaskOutput)
      Log.info("Completed " + originalProjectAndTask + ", took" + sw + "\n")
    result
  }
}
