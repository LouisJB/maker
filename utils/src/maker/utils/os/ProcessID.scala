package maker.utils.os

import java.lang.management.ManagementFactory

case class ProcessID(id : Int){
  def isRunning = {
    val status = Command("kill", "-0", id.toString).exec()
    status == 0
  }
  def kill{
    val status = Command("kill", "-9", id.toString).exec()
    assert(status == 0, "Failed to kill process " + id + ", ")
  }
}

object ProcessID{
  def apply() : ProcessID = {
    val List(idString, host) = ManagementFactory.getRuntimeMXBean().getName().split("@").toList
    ProcessID(idString.toInt)
  }
  def apply(proc : Process) : ProcessID = {
    assert(OsUtils.isUnix, "TODO - find out how to get process ID on a windows box")
    val f = proc.getClass().getDeclaredField("pid")
    f.setAccessible(true)
    ProcessID(f.getInt(proc))
  }
}

