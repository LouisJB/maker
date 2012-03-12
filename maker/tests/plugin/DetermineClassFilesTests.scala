package plugin

import maker.project.Project
import maker.utils.FileUtils._
import maker._
import org.scalatest.FunSuite
import java.io.File

class DetermineClassFilesTests extends FunSuite{
  def withTestProject(f : Project => Unit){
    withTempDir{
      dir => 
        val proj = new Project(
          "test", dir, List(file(dir, "src")), Nil,
          libDirs=List(file(".maker/lib"))
      )
        f(proj)
    } 
  }
  test("Class files produced for simple class"){
    withTestProject{
      proj =>
        val fooSrc = file(proj.root, "src/foo/Foo.scala")
        writeToFile(
          fooSrc,
          """
            package foo
            case class Foo(x : Double)
          """
        )
        val barSrc = file(proj.root, "src/Bar.scala")
        writeToFile(
          barSrc,
          """
            package foo.bar
            case class Bar(x : Double){
              object Bob
            }

            object Bar{
              class Fax(z : Int)
            }
          """
        )
        val bazSrc = file(proj.root, "src/foo/bar/Baz.scala")
        writeToFile(
          bazSrc,
          """
            package foo.bar
            object Baz{
              case class Mike(x : Double){
                object Bob
              }
            }
          """
        )
        proj.compile
        val srcFiles = List(fooSrc, barSrc, bazSrc)
        val allPossibleClassAndObjectFiles = (Set[File]() /: srcFiles.map(proj.state.classFilesFor))(_++_)
        assert(proj.classFiles.forall(allPossibleClassAndObjectFiles))
    }
  }
}
