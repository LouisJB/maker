package maker.task.tasks

import org.scalatest.FunSuite
import maker.utils.FileUtils._
import maker.utils.RichString._
import maker.project.Project

class RunMainTaskTests extends FunSuite{
  test("Can run task"){
    withTempDir({
      dir ⇒ 
        val proj = Project.makeTestProject("RunMainTaskTests", dir)
        val outputFile = file(dir, "output.txt")
        assert(! outputFile.exists)
        proj.writeSrc(
          "foo/Main.scala",
          """
            package foo

            import java.io._

            object Main extends App{
              
              val file = new File("%s")
              val fstream = new FileWriter(file)
              val out = new BufferedWriter(fstream)
              out.write("Hello")
            }
          """ % outputFile.getAbsolutePath
        )
        proj.compile
        proj.runMain("foo.Main")()()
        assert(outputFile.exists)
    })
  }
}
