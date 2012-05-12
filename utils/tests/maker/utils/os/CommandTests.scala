package maker.utils.os

import org.scalatest.FunSuite
import maker.utils.FileUtils._
import scala.actors.Futures

class CommandTests extends FunSuite{

  test("synchronous command runs"){
    withTempDir{
      dir ⇒
      val f = file(dir, "foo")
        assert(! f.exists)
      val cmd = Command(CommandOutputHandler.NULL, None, "touch", f.getAbsolutePath)
        cmd.exec
      assert(f.exists)
    }
  }

  test("asynchronous command runs"){
    withTempDir{
      dir ⇒
      val f = file(dir, "foo")
        assert(! f.exists)
      val cmd = Command(CommandOutputHandler.NULL, None, "touch", f.getAbsolutePath)
        val (_, future) = cmd.execAsync
      val result = Futures.awaitAll(1000, future).head
      assert(f.exists)
      assert(result == Some(0))
    }
  }

  test("Output is written to file"){
    withTempDir{
      dir ⇒
      val outputFile = file(dir, "output")
        assert(! outputFile.exists)
      val cmd = Command(CommandOutputHandler(outputFile), None, "echo", "HELLO")
        cmd.exec
      assert(outputFile.exists)
      val lines = outputFile.read.toList
      assert(lines === List("HELLO"))
    }
  }

  test("Output is saved"){
    withTempDir{
      dir ⇒
      val cmd = Command(CommandOutputHandler.NULL.withSavedOutput, None, "echo", "HELLO")
        cmd.exec
      assert(cmd.savedOutput === "HELLO")
    }
  }

  test("Can kill process whose output is being redirected"){
    withTempDir{
      dir ⇒ 
        writeToFile(
          file(dir, "main.sh"),
          """
          while true; do
            echo `date`
            sleep 1
          done
          """
        )
        val cmd = new Command(new CommandOutputHandler().withSavedOutput, Some(dir), "bash", "main.sh")
        val (proc, future) = cmd.execAsync
        val procID = ProcessID(proc)
        assert(procID.isRunning)
        assert(! future.isSet)
        proc.destroy
        Futures.awaitAll(10000, future)
        assert(!procID.isRunning, "Process should have died")
        assert(future.isSet)
    }
  }


  /**
   * Test is really to ensure my understanding of Java Processes is correct
   */
  test("Can kill process even if its output is not consumed"){
    withTempDir{
      dir ⇒ 
        writeToFile(
          file(dir, "main.sh"),
          """
          while true; do
            echo `date`
            sleep 1
          done
          """
        )
        val cmd = new Command(CommandOutputHandler.NO_CONSUME_PROCESS_OUTPUT, Some(dir), "bash", "main.sh")
        val (proc, future) = cmd.execAsync
        val procID = ProcessID(proc)
        assert(procID.isRunning)
        assert(! future.isSet)
        proc.destroy
        Futures.awaitAll(10000, future)
        assert(!procID.isRunning, "Process should have died")
        assert(future.isSet)
    }
  }
}

