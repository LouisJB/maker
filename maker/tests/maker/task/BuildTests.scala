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
    
  val fooTestContent = 
    """
    package foo
    import org.scalatest.FunSuite
    class FooTest extends FunSuite{
      test("test foo"){
        val foo1 = Foo(1.0)
        val foo2 = Foo(1.0)
        assert(foo1 === foo2)
      }
    }
    """

  val failingTestContent = 
    """
    package foo
    import org.scalatest.FunSuite
    class FooTest extends FunSuite{
      test("test foo"){
        assert(1 === 2)
      }
    }
    """
  def makeProject(name : String, root : File) = {
    Project(
      name, 
      root, 
      List(new File(root, "src")), 
      List(new File(root, "tests")), 
      Nil
    )
  }
  test("Build of single project"){
    val root = tempDir("fred")
      val proj = makeProject("foox", root)

      writeToFile(new File(root, "src/foo/Foo.scala"), fooContent)
    assert(Build2(proj, CompileSourceTask()) === BuildResult2(Right("OK")))
    proj.delete
  }

  test("Build of dependent projects"){
    val root1 = tempDir("fred")
      val root2 = tempDir("fred")
      val proj1 = makeProject("1", root1)
      val proj2 = makeProject("2", root2) dependsOn proj1

    writeToFile(new File(root1, "src/foo/Foo.scala"), fooContent)
    writeToFile(new File(root2, "src/bar/Bar.scala"), barContent)
    assert(Build2(proj1, CompileSourceTask()) === BuildResult2(Right("OK")))
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
    Build2(proj2, CompileSourceTask()) match {
      case BuildResult2(Left(taskFailure)) =>
      case r => fail("Expected build to fail, got " + r)
    }
    proj1.delete
    proj2.delete
  }

  test("Unit test runs"){
    val root = tempDir("fred")
    val proj = makeProject("foo_with_test", root)
    writeToFile(new File(root, "src/foo/Foo.scala"), fooContent)
    writeToFile(new File(root, "tests/foo/FooTest.scala"), fooTestContent)
    assert(Build2(proj, TestTask()) === BuildResult2(Right("OK")))
    proj.delete
  }

  test("Failing test fails"){
    val root = tempDir("fred")
    val proj = makeProject("with_failing_test", root)
    writeToFile(new File(root, "tests/foo/FooTest.scala"), failingTestContent)
    Build2(proj, TestTask()) match {
      case BuildResult2(Left(_)) =>
      case r => fail("Expected test to fail, got " + r)
    }
    proj.delete
  }
}
