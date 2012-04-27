package maker.task

import org.scalatest.FunSuite
import akka.actor.ActorRef
import akka.util.Timeout
import akka.dispatch.Await
import akka.pattern.ask
import akka.util.Duration
import akka.actor.Actor
import akka.actor.{Props ⇒ AkkaProps}
import akka.actor.ActorSystem

class RemoteTaskManagerTests extends FunSuite{

  def waitForResponse(actor : ActorRef, msg : Any) : Any = {
    implicit val timeout = Timeout(10000)
    val future = actor ? msg
    val result = Await.result(future, Duration.Inf)
    result
  }

  test("Remote process id is different to this one"){
    def launchTestAndShutdown{
      var taskRunner = new RemoteTaskManager
      taskRunner.initialise
      val thisProcessID = ProcessID()
      val thatProcessID = waitForResponse(taskRunner.actor, RemoteTaskManager.GetRemoteProcessID)
      assert(thisProcessID != thatProcessID)
      taskRunner.shutdown
    }
    launchTestAndShutdown
    launchTestAndShutdown
  }
}
