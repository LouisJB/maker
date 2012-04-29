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
import maker.project.Project
import maker.utils.FileUtils._
import java.io.File
import maker.Props

class RemoteTaskManagerTests extends FunSuite{

  def waitForResponse(actor : ActorRef, msg : Any) : Any = {
    implicit val timeout = Timeout(10000)
    val future = actor ? msg
    val result = Await.result(future, Duration.Inf)
    result
  }

  test("Remote process id is different to this one"){
    val propsFile = Props(file("Maker.conf"))
    def mkProject(name : String, libs : List[File] = Nil) = new Project(
      name, file(name),
      libDirs=libs,
      resourceDirs = List(file(name + "/resources")),
      props = propsFile
    )

    lazy val utils = mkProject("utils", List(file("utils/maker-lib"), file("libs/")))
    lazy val plugin = mkProject("plugin") dependsOn utils
    lazy val makerProj = mkProject("maker") dependsOn plugin

    lazy val mkr = makerProj
    def launchTestAndShutdown{
      var taskRunner = new RemoteTaskManager(mkr.runClasspath)
      taskRunner.initialise
      val thisProcessID = ProcessID()
      val thatProcessID = waitForResponse(taskRunner.actor, RemoteTaskManager.GetRemoteProcessID)
      assert(thisProcessID != thatProcessID)
      taskRunner.shutdown
    }
    //launchTestAndShutdown
    //launchTestAndShutdown
  }
}
