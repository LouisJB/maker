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
    case ExecTaskMessage(projectTask : ProjectAndTask, acc : Map[Task, List[AnyRef]], parameters : Map[String, String]) => sender ! {
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

case class BuildResult[+A](res : Either[TaskFailed, A],
                       projectAndTasks : Set[ProjectAndTask],
                       originalProjectAndTask : ProjectAndTask) extends BuildMessage {

  self =>
  import maker.graphviz.GraphVizDiGrapher._
  import maker.graphviz.GraphVizUtils._

  def stats : List[String] = projectAndTasks.map(_.allStats).toList
  def linearTime : Long = projectAndTasks.map(_.runTimeMs).toList.sum

  def resultTree(pt : ProjectAndTask = originalProjectAndTask) =
    pt.getTaskTree.map(p => (projectAndTasks.find(_ == p._1).get, p._2.map(pt => projectAndTasks.find(_ == pt).get)))

  def showBuildGraph() : Option[File] =
    resultTree() match {
      case Nil | null => Log.info("No project results to graph"); None
      case r => Some(showGraph(makeDotFromProjectAndTask(r)))
    }

  def filterByTask(t : Task) = projectAndTasks.filter(p => p == ProjectAndTask(p.project, t))

  override def toString = res.toString

  @inline
  final def flatMap[B](f : A => BuildResult[B]) : BuildResult[B] = {
    res match {
      case Left(l) =>
        BuildResult(Left(l) : Either[TaskFailed, B], projectAndTasks, originalProjectAndTask)
      case Right(a) => {
        val br : BuildResult[B] = f(a)
        br.copy(projectAndTasks = projectAndTasks ++ br.projectAndTasks)
      }
    }
  }

  @inline
  final def map[B](f: A => B): BuildResult[B] = {
    val mappedRes = res match {
      case Left(l) => Left(l) : Either[TaskFailed, B]
      case Right(_) => res.right.map(f)
    }
    BuildResult(mappedRes, projectAndTasks, originalProjectAndTask)
  }

  def filter(f: A => Boolean): BuildResult[A] = this

  class WithFilter(p: A => Boolean) {
    println("with filter!")
    def map[B](f: A => B): BuildResult[B] = self.filter(p).map(f)
    def flatMap[B](f: A => BuildResult[B]): BuildResult[B] = self.filter(p).flatMap(f)
   // def foreach[U](f: A => U): Unit = self.filter(p).foreach(f)
    def withFilter(q: A => Boolean): WithFilter =
      new WithFilter(x => p(x) && q(x))
  }
  /** called with conditional statement in for comprehension */
  def withFilter(p: A => Boolean): WithFilter = new WithFilter(p)

/*
  @inline
  def withFilter(f: A => Boolean) = {
    res match {
      case Left(l) => false
      case Right(a) => f(a)
    }
  }
*/
}
