package maker.utils.os

import org.scalatest.FunSuite
import maker.utils.Log

class ProcessIDTests extends FunSuite{

  test("Can construct a process id"){
    val p = ProcessID()
      assert(p.id > 0)
  }
}
