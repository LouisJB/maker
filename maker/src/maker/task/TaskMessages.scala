package maker.task

import akka.actor.Actor
import java.io.File
import maker.utils.Log

trait TaskResult

case object TaskSuccess extends TaskResult {
  override def toString = "OK"
}
case class TaskError(reason : String, exception : Option[Throwable]) extends TaskResult {
  override def toString = "Task error, reason: " + reason + exception.map(e => ", exception " + e.getMessage).getOrElse("")
}

sealed trait BuildMessage

case class ExecTaskMessage(projectTask : ProjectAndTask,
                           acc : Map[Task, List[AnyRef]],
                           parameters : Map[String, String] = Map()) extends BuildMessage

case class TaskResultMessage(projectTask : ProjectAndTask, result : Either[TaskFailed, AnyRef]) extends BuildMessage

case object StartBuild extends BuildMessage

class Worker() extends Actor{
  def receive = {
    case ExecTaskMessage(projectTask : ProjectAndTask, acc : Map[Task, List[AnyRef]], parameters : Map[String, String]) => self reply {
      try {
        TaskResultMessage(projectTask, projectTask.exec(acc, parameters))
      }
      catch {
        case e =>
          TaskResultMessage(projectTask, Left(TaskFailed(projectTask, e.getMessage)))
      }
    }
  }
}

case class BuildResult(res : Either[TaskFailed, AnyRef],
                       projectAndTasks : Set[ProjectAndTask],
                       originalProjectAndTask : ProjectAndTask) extends BuildMessage {
  import maker.graphviz.GraphVizDiGrapher._
  import maker.graphviz.GraphVizUtils._

  def stats : List[String] = projectAndTasks.map(_.allStats).toList

  def resultTree(pt : ProjectAndTask = originalProjectAndTask) =
    pt.getTaskTree.map(p => (projectAndTasks.find(_ == p._1).get, p._2.map(pt => projectAndTasks.find(_ == pt).get)))

  def showBuildGraph() : Option[File] =
    resultTree() match {
      case Nil | null => Log.info("No project results to graph"); None
      case r => Some(showGraph(makeDotFromProjectAndTask(r)))
    }

  override def toString = res.toString
}