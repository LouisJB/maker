package maker.task
import java.lang.management.ManagementFactory

case class ProcessID(host : String, id : Int)

object ProcessID{
  def apply() : ProcessID = {
    val List(idString, host) = ManagementFactory.getRuntimeMXBean().getName().split("@").toList
    ProcessID(host, idString.toInt)
  }
}

