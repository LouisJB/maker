package maker.task
import java.lang.management.ManagementFactory
import maker.os.Command

case class ProcessID(host : String, id : Int){
  def isRunning = {
    assert(host == ProcessID().host, "Can only check status of process running on machine " + host)
    val (status, _) = Command("kill", "-0", id.toString).exec()
    status == 0
  }
  def kill{
    val (status, output) = Command("kill", "-9", id.toString).exec()
    assert(status == 0, "Failed to kill process " + id + ", " + output)
  }
}

object ProcessID{
  def apply() : ProcessID = {
    val List(idString, host) = ManagementFactory.getRuntimeMXBean().getName().split("@").toList
    ProcessID(host, idString.toInt)
  }

}

