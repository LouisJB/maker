package maker.task

import org.scalatest.FunSuite

class RemoteTaskManagerTests extends FunSuite{
  val classpath = ""
  test("can create remote process and bring it down"){
    val client = new RemoteTaskManagerClient
    client.launch
    //assert(manager.isRunning)
    //manager.stop
    //assert(! manager.isRunning)
  }
}
