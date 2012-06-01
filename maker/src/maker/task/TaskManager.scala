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

class TaskManager(tree : DependencyTree[ProjectAndTask], router : ActorRef,
                   originalProjectAndTask : ProjectAndTask,
                   parameters : Map[String, String] = Map()) extends Actor {

  Log.debug("About to exec deps of " + originalProjectAndTask)
  Log.debug("Tree is " + tree)
  var accumuland : Map[Task, List[AnyRef]] = Map[Task, List[AnyRef]]()
  var remainingTree = tree
  var completedProjectTasks : Set[ProjectAndTask] = Set()
  var allProjectTasks = tree.all
  var originalCaller : ActorRef = _
  var roundNo = 0
  private def execNextLevel{
    val nextToBeExecuted = remainingTree.childless
    nextToBeExecuted.foreach{
      pt =>

        pt.roundNo = roundNo
        router ! ExecTaskMessage(pt, accumuland, parameters)
    }
    roundNo += 1
  }
  def receive = {
    case TaskResultMessage(projectTask, Left(taskFailure)) => {
      ProjectAndTask.removeTask(projectTask)
      //router.stop
      originalCaller ! BuildResult(Left(taskFailure), tree, originalProjectAndTask)
    }
    case TaskResultMessage(projectTask, Right(result)) => {
      ProjectAndTask.removeTask(projectTask)
      accumuland = accumuland + (projectTask.task -> (result :: accumuland.getOrElse(projectTask.task, Nil)))
      completedProjectTasks += projectTask
      if (completedProjectTasks == allProjectTasks)
        originalCaller ! BuildResult(Right(TaskSuccess), tree, originalProjectAndTask)
      else {
        remainingTree = remainingTree - projectTask
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

  def apply(tree : DependencyTree[ProjectAndTask], originalProjectAndTask : ProjectAndTask, parameters : Map[String, String]) : BuildResult[AnyRef] = {
    assert(tree.parents.contains(originalProjectAndTask), "Task tree mis-specified")
    Thread.currentThread.setContextClassLoader(TaskManager.getClass.getClassLoader)
    val system = ActorSystem("QueueManager")
    val sw = Stopwatch()
    implicit val timeout = Timeout(1000000)
    def nWorkers = (Runtime.getRuntime.availableProcessors / 2) max 1

    Log.debug("Running with " + nWorkers + " workers")
    val router = system.actorOf(Props[Worker].withRouter(SmallestMailboxRouter(nWorkers)))
    val qm = system.actorOf(Props(new TaskManager(tree, router, originalProjectAndTask, parameters)))
    
    // wait for build to complete or user termination
    def startAndMonitor() : BuildResult[AnyRef] = {
      Log.debug("Starting task %s, press %s to terminate".format(originalProjectAndTask, Task.termSym))
      val future = qm ? StartBuild
      def getResult() : Option[BuildResult[AnyRef]] = {
        try {
          Await.result(future, Duration(1, SECONDS)) match {
            case v @ BuildResult(_, _, _) => Some(v.asInstanceOf[BuildResult[AnyRef]])
            case _ => None
          }
        }
        catch {
          case _ : TimeoutException => {
            if (System.in.available > 0 && System.in.read == Task.termChar) {
              Log.info("Terminating build...")
              system.shutdown()
              Some(BuildResult(Left(TaskFailed(originalProjectAndTask, "User terminated")) : Either[TaskFailed, AnyRef], tree, originalProjectAndTask))
            }
            else None
          }
        }
      }
      Stream.continually[Option[BuildResult[AnyRef]]](getResult()).dropWhile(_.isEmpty).head.get
    }
    val result : BuildResult[AnyRef] = startAndMonitor()

    TaskResults.addResult(result)

    system.shutdown()

    Log.debug("Stats: \n" + tree.all.map(_.runStats).mkString("\n"))
    if (! originalProjectAndTask.project.suppressTaskOutput)
      Log.info("Completed " + originalProjectAndTask + ", took" + sw + "\n")
    result
  }
}
