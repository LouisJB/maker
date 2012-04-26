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

//<<<<<<< HEAD
//trait TaskResult
//case object TaskSuccess extends TaskResult { 
  //override def toString = "OK"
  //}
  //case class TaskError(reason : String, exception : Option[Throwable]) extends TaskResult {
    //override def toString = "Task error, reason: " + reason + exception.map(e => ", exception " + e.getMessage).getOrElse("")
  //}
  //
  //sealed trait BuildMessage
  //case class ExecTaskMessage(projectTask : ProjectAndTask,
    //acc : Map[Task, List[AnyRef]],
    //parameters : Map[String, String] = Map()) extends BuildMessage
  //case class TaskResultMessage(projectTask : ProjectAndTask, result : Either[TaskFailed, AnyRef]) extends BuildMessage
  //case object StartBuild extends BuildMessage
  //class Worker() extends Actor{
    //def receive = {
      //case ExecTaskMessage(projectTask : ProjectAndTask, acc : Map[Task, List[AnyRef]], parameters : Map[String, String]) => sender ! {
        //try {
          //TaskResultMessage(projectTask, projectTask.exec(acc, parameters))
          //} catch {
            //case e =>
            //TaskResultMessage(projectTask, Left(TaskFailed(projectTask, e.getMessage)))
            //}
            //}
            //}
            //}
            //
            //case class BuildResult(res : Either[TaskFailed, AnyRef], projectAndTasks : Set[ProjectAndTask], originalProjectAndTask : ProjectAndTask) extends BuildMessage {
              //import maker.graphviz.GraphVizDiGrapher._
              //import maker.graphviz.GraphVizUtils._
              //def stats = projectAndTasks.map(_.allStats).mkString("\n")
              // 
              //def resultTree(pt : ProjectAndTask = originalProjectAndTask) =
              //pt.getTaskTree.map(p => (projectAndTasks.find(_ == p._1).get, p._2.map(pt => projectAndTasks.find(_ == pt).get)))
              // 
              //def showBuildGraph() : Option[File] =
              //resultTree() match {
                //case Nil | null => Log.info("No project results to graph"); None
                //case r => Some(showGraph(makeDotFromProjectAndTask(r)))
                //}
                //
                //override def toString = res.toString
                //}
                //
                //class QueueManager(projectTasks : Set[ProjectAndTask], router : ActorRef,
                  //||||||| merged common ancestors
                  //trait TaskResult
                  //case object TaskSuccess extends TaskResult { 
                    //override def toString = "OK"
                    //}
                    //case class TaskError(reason : String, exception : Option[Throwable]) extends TaskResult {
                      //override def toString = "Task error, reason: " + reason + exception.map(e => ", exception " + e.getMessage).getOrElse("")
                    //}
                    //
                    //sealed trait BuildMessage
                    //case class ExecTaskMessage(projectTask : ProjectAndTask,
                      //acc : Map[Task, List[AnyRef]],
                      //parameters : Map[String, String] = Map()) extends BuildMessage
                    //case class TaskResultMessage(projectTask : ProjectAndTask, result : Either[TaskFailed, AnyRef]) extends BuildMessage
                    //case object StartBuild extends BuildMessage
                    //class Worker() extends Actor{
                      //def receive = {
                        //case ExecTaskMessage(projectTask : ProjectAndTask, acc : Map[Task, List[AnyRef]], parameters : Map[String, String]) => self reply {
                          //try {
                            //TaskResultMessage(projectTask, projectTask.exec(acc, parameters))
                            //} catch {
                              //case e =>
                              //TaskResultMessage(projectTask, Left(TaskFailed(projectTask, e.getMessage)))
                              //}
                              //}
                              //}
                              //}
                              //
                              //case class BuildResult(res : Either[TaskFailed, AnyRef], projectAndTasks : Set[ProjectAndTask], originalProjectAndTask : ProjectAndTask) extends BuildMessage {
                                //import maker.graphviz.GraphVizDiGrapher._
                                //import maker.graphviz.GraphVizUtils._
                                //def stats = projectAndTasks.map(_.allStats).mkString("\n")
                                // 
                                //def resultTree(pt : ProjectAndTask = originalProjectAndTask) =
                                //pt.getTaskTree.map(p => (projectAndTasks.find(_ == p._1).get, p._2.map(pt => projectAndTasks.find(_ == pt).get)))
                                // 
                                //def showBuildGraph() : Option[File] =
                                //resultTree() match {
                                  //case Nil | null => Log.info("No project results to graph"); None
                                  //case r => Some(showGraph(makeDotFromProjectAndTask(r)))
                                  //}
                                  //
                                  //override def toString = res.toString
                                  //}
                                  //
                                  //class QueueManager(projectTasks : Set[ProjectAndTask], router : ActorRef,
                                    //=======
class TaskManager(projectTasks : Set[ProjectAndTask], router : ActorRef,
  //>>>>>>> origin/master
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
    Log.info("Queue manager - tasks = " + projectTasks.mkString(", "))
    val sw = Stopwatch()
    implicit val timeout = Timeout(1000000)
    def nWorkers = (Runtime.getRuntime.availableProcessors / 2) max 1

    Log.debug("Running with " + nWorkers + " workers")
    val router = system.actorOf(Props[Worker].withRouter(SmallestMailboxRouter(nWorkers)))
      //val router = Routing.loadBalancerActor(CyclicIterator(workers)).start()
    val qm = system.actorOf(Props(new TaskManager(projectTasks, router, originalProjectAndTask, parameters)))
    val future = qm ? StartBuild
    val result = Await.result(future, Duration.Inf).asInstanceOf[BuildResult[AnyRef]]

    //import akka.actor.PoisonPill
    //qm ! PoisonPill
    ////workers.foreach(_ ! PoisonPill)
    //router ! PoisonPill
    system.shutdown()
    Log.debug("Stats: \n" + projectTasks.map(_.runStats).mkString("\n"))
    Log.info("Completed " + originalProjectAndTask + ", took" + sw + ", result " + result)
    result
  }
}
