package maker.task
import org.scalatest.FunSuite
import maker.remoteakka.ProcessID

class RemoteTaskRunnerTests extends FunSuite{

  test("Remote process id is different to this one"){
    val taskRunner = new RemoteTaskRunner
    taskRunner.initialise
    val thisProcessID = ProcessID()
    val thatProcessID = taskRunner.askRemoteForProcessID
    assert(thisProcessID != thatProcessID)
    taskRunner.shutdown
  }
}
