package maker.task

import maker.Maker
import maker.os.ScalaCommand

object RemoteTaskManager extends App{

  println("Running RemoteTaskManager")


}

class RemoteTaskManagerClient{

  def launch{
    val cmd = ScalaCommand(Maker.mkr.runClasspath, "maker.task.RemoteTaskManager")

    cmd.execAsync
  }
}
