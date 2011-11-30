package maker.task

import org.scalatest.FunSuite
import maker.utils.FileUtils._
import maker.project.Project
import java.io.File

class BuildTests extends FunSuite {

  val fooContent = 
    """
    package foo
    case class Foo(x : Double){
      val fred = 10
      def double() = x + x
    }
    """

  val barContent = 
    """
    package bar
    import foo.Foo
    case class Bar(f : Foo){
      val fred = 10
      def double() = f.x + f.x
    }
    """

  val barContentWithError = 
    """
    package bar
    import foo.Foo
    case class B ar(f : Foo){
      val fred = 10
      def doub le() = f.x + f.x
    }
    """
    
  def makeProject(name : String, root : File) = {
    Project(
      name, 
      root, 
      List(new File(root, "src")), 
      Nil,
      Nil
    )
  }
  test("Build of single project"){
    val root = tempDir("fred")
    val proj = makeProject("foox", root)

    writeToFile(new File(root, "src/foo/Foo.scala"), fooContent)
    assert(Build(Set(proj)) === BuildResult(true))
    proj.delete
  }

  test("Build of dependent projects"){
    val root1 = tempDir("fred")
    val root2 = tempDir("fred")
    val proj1 = makeProject("1", root1)
    val proj2 = makeProject("2", root2) dependsOn proj1

    writeToFile(new File(root1, "src/foo/Foo.scala"), fooContent)
    writeToFile(new File(root2, "src/bar/Bar.scala"), barContent)
    assert(Build(Set(proj1, proj2)) === BuildResult(true))
    proj1.delete
    proj2.delete
  }

  test("Build of dependent projects with compilation error fails"){
    val root1 = tempDir("fred")
    val root2 = tempDir("fred")
    val proj1 = makeProject("1", root1)
    val proj2 = makeProject("2", root2) dependsOn proj1

    writeToFile(new File(root1, "src/foo/Foo.scala"), fooContent)
    writeToFile(new File(root2, "src/bar/Bar.scala"), barContentWithError)
    assert(Build(Set(proj1, proj2)) === BuildResult(false))
    proj1.delete
    proj2.delete
  }
}
