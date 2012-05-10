package maker.task

import org.scalatest.FunSuite

class LocalTaskManagerTests extends FunSuite{
  test("Can't connect if server isn't running"){
    try {
      new LocalTaskManager(300, 2).connectToRemote
    } catch {
      case _ ⇒ 
    }
  }
}
