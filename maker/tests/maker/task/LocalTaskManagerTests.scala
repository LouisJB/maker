package maker.task

import org.scalatest.FunSuite
import maker.utils.os.ProcessID

class LocalTaskManagerTests extends FunSuite{

  test("Can't connect to remote server if local task manager isn't running"){
    try {
      new LocalTaskManager(300, 2).connectToRemote
      fail
    } catch {
      case CannotConnectToRemoteServer ⇒ 
      case other ⇒ throw other
    }
  }

  private def withRemoteTaskManager(ltm : LocalTaskManager = new LocalTaskManager(500, 5))(fn : LocalTaskManager ⇒ _){
    //val ltm = new LocalTaskManager(500, 3)
    try {
      ltm.launchRemote
      fn(ltm)
    } finally {
      ltm.closeRemote
    }
  }

  test("Can launch remote server and bring it down"){
    val ltm = new LocalTaskManager(800, 5)
    withRemoteTaskManager(ltm){
      ltm : LocalTaskManager ⇒ 
        assert(ltm.isRemoteRunning)
    }
    assert(! ltm.isRemoteRunning)

    // and can do it again
    withRemoteTaskManager(ltm){
      ltm : LocalTaskManager ⇒ 
        assert(ltm.isRemoteRunning)
    }
    assert(! ltm.isRemoteRunning)
  }

  test("Can send a message to remote server"){
    withRemoteTaskManager(){
      ltm : LocalTaskManager ⇒ 
      assert(ltm.isRemoteRunning)
      ltm.connectToRemote
      ltm.sendMessage(TellMeYourProcessID) match {
        case ProcessID(pid) ⇒ 
          assert(pid != ProcessID().id)
        case other ⇒ 
          fail("Expected to receive ProcessID, got " + other)
      }
    }
  }
}
