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
        val cmd = Command(CommandOutputHandler.NULL, "touch", f.getAbsolutePath)
        cmd.exec
        assert(f.exists)
    }
  }

  test("asynchronous command runs"){
    withTempDir{
      dir ⇒
        val f = file(dir, "foo")
        assert(! f.exists)
        val cmd = Command(CommandOutputHandler.NULL, "touch", f.getAbsolutePath)
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
        val cmd = Command(CommandOutputHandler(outputFile), "echo", "HELLO")
        cmd.exec
        assert(outputFile.exists)
        val lines = outputFile.read.toList
        assert(lines === List("HELLO"))
    }
  }

  test("Output is saved"){
    withTempDir{
      dir ⇒
        val cmd = Command(CommandOutputHandler.NULL.withSavedOutput, "echo", "HELLO")
        cmd.exec
        assert(cmd.savedOutput === "HELLO")
    }
  }
}

