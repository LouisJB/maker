package maker.task

import maker.project.Project
import akka.actor.Actor
import java.io.File
import akka.routing.Routing
import akka.routing.CyclicIterator
import Actor._
import Routing._
import akka.actor.PoisonPill
import akka.actor.Channel
import akka.actor.ActorRef
import akka.actor.UntypedChannel

sealed trait BuildMessage
case class CompileMessage(proj : Project) extends BuildMessage
case class CompileResultMessage(proj : Project, result : Either[TaskFailed, Set[File]]) extends BuildMessage
case class BuildResult(result : Boolean) extends BuildMessage
case object StartBuild extends BuildMessage


class Worker() extends Actor{
  def receive = {
    case CompileMessage(proj : Project) => self reply CompileResultMessage(proj, proj.compile)
  }
}

class QueueManager(projects : Set[Project], nWorkers : Int) extends Actor{
  val workers = Vector.fill(nWorkers)(actorOf[Worker].start)
  val router = Routing.loadBalancerActor(CyclicIterator(workers)).start()
  var remainingProjects = projects
  var completedProjects : Set[Project] = Set()
  var originalCaller : UntypedChannel = _
  private def compileNextLevel{
    val (canBeProcessed, mustWait) = remainingProjects.partition(
      _.dependentProjects.filterNot(remainingProjects).isEmpty
    )
    remainingProjects = mustWait
    canBeProcessed.foreach(router ! CompileMessage(_))
  }
  def receive = {
    case CompileResultMessage(_, _ : Left[_, _]) => {
      router ! PoisonPill
      originalCaller ! BuildResult(false)
    }
    case CompileResultMessage(proj, _ : Right[_, _]) => {
      completedProjects += proj
      if (completedProjects  == projects)
        originalCaller ! BuildResult(true)
      else {
        remainingProjects = remainingProjects.filterNot(_ == proj)
        compileNextLevel
      }
    }
    case StartBuild => {
      originalCaller = self.channel
      compileNextLevel
    }
  }
}

object Build{
  def apply(projects : Set[Project]){
    implicit val timeout = Timeout(10000)
    val future = actorOf(new QueueManager(projects, 2)).start ? StartBuild
    println("Result was " + future.get)
  }
}
