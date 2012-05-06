package maker.task
import org.scalatest.FunSuite
import maker.utils.os.OsUtils
import maker.os.Command

class ProcessIDTests extends FunSuite{
  test("Can construct a process id"){
    val p = ProcessID()
      assert(p.id > 0)
  }
  test("Can kill running process"){
    if (OsUtils.isUnix){
      val proc = Command("vi").execAsync
    }

  }
}
