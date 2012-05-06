package maker.utils.os

import org.scalatest.FunSuite
import maker.utils.Log

class ProcessIDTests extends FunSuite{

  test("Can construct a process id"){
    val p = ProcessID()
      assert(p.id > 0)
  }

  test("Can kill running process on Unix"){
    if (OsUtils.isUnix){
      //val proc = Command("vi").withNullOutput.execAsync
      //val pid = ProcessID(proc)
      //Log.info("Proc " + pid)
      //assert(pid.isRunning)
      //pid.kill
      //assert(!pid.isRunning)
    }

  }
}
