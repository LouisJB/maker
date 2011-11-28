package maker.task

import org.scalatest.FunSuite
import maker.utils.FileUtils._
import maker.project.Project
import java.io.File

class BuildTests extends FunSuite {
  val root = tempDir("fred")
  var proj : Project = _
  val fooSrc = new File(root, "src/foo/Foo.scala")
  def fooClass = new File(proj.outputDir, "foo/Foo.class")
  def fooObject = new File(proj.outputDir, "foo/Foo$.class")

  val originalFooContent = 
    """
    package foo
    case class Foo(x : Double){
      val fred = 10
      def double() = x + x
    }
    """
  def writeStandardProject{
    proj = Project(
      "foox", 
      root, 
      List(new File(root, "src")), 
      Nil,
      Nil
    )

    writeToFile(fooSrc, originalFooContent)
  }

  test("Build of single project"){
    writeStandardProject
    Build(Set(proj))
  }
}
