package maker.task

import org.scalatest.FunSuite
import maker.utils.FileUtils._
import maker.project.Project
import maker.project.Project._
import java.io.File
import maker.utils.Log

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
  test("Build of single project"){
    val root = tempDir("fred")
    val proj = makeTestProject("foox", root)

    writeToFile(new File(root, "src/foo/Foo.scala"), fooContent)
    assert(proj.compile.res.isRight)
    proj.delete
  }

  test("Build of dependent projects"){
    withTempDir( {
      root ⇒ 
        val root1 = file(root, "proj1")
        val root2 = file(root, "proj2")
        val proj1 = makeTestProject("1", root1)
        val proj2 = makeTestProject("2", root2) dependsOn proj1

        writeToFile(new File(root1, "src/foo/Foo.scala"), fooContent)
        writeToFile(new File(root2, "src/bar/Bar.scala"), barContent)
        proj2.compile
    },
    false)
  }

  test("Build of dependent projects with compilation error fails"){
    val root1 = tempDir("fred")
      val root2 = tempDir("fred")
      val proj1 = makeTestProject("1", root1)
      val proj2 = makeTestProject("2", root2) dependsOn proj1

    writeToFile(new File(root1, "src/foo/Foo.scala"), fooContent)
    writeToFile(new File(root2, "src/bar/Bar.scala"), barContentWithError)
    proj2.compile match {
      case BuildResult(Left(taskFailure), _, _) =>
      case r => fail("Expected build to fail, got " + r)
    }
    proj1.delete
    proj2.delete
  }

  test("Unit test runs"){
    val root = tempDir("fred")
      val proj = makeTestProject("foo_with_test", root)
      writeToFile(new File(root, "src/foo/Foo.scala"), fooContent)
    writeToFile(new File(root, "tests/foo/FooTest.scala"), fooTestContent)
    proj.compile
    val fooClass = proj.classLoader.loadClass("foo.Foo")


      assert(
      proj.test.res.isRight 
    )

    proj.delete
  }

  test("Failing test fails"){
    val root = tempDir("fred")
    val proj = makeTestProject("with_failing_test", root)
    writeToFile(new File(root, "tests/foo/FooTest.scala"), failingTestContent)

    proj.test match {
      case BuildResult(Left(_), _, _) =>
      case r => fail("Expected test to fail, got " + r)
    }
    proj.delete
  }

  test("Can re-run failing tests"){
    val root = tempDir("fred")
      val proj = makeTestProject("rerun_failing_test", root)
      writeToFile(
      new File(root, "tests/foo/GoodTest.scala"), 
      """
      package foo
      import org.scalatest.FunSuite
      class GoodTest extends FunSuite{
        test("test foo"){
          assert(1 === 1)
        }
      }
      """
    )
    writeToFile(
      new File(root, "tests/foo/BadTest.scala"), 
      """
      package foo
      import org.scalatest.FunSuite
      class BadTest extends FunSuite{
        test("test foo"){
          assert(1 === 2)
        }
      }
      """
    )
    proj.test
    assert(proj.testResults.failed.size == 1)
    assert(proj.testResults.passed.size == 1)

    //This time we should only run the failed test
    //so there should be no passing tests
    proj.testFailingSuites
    assert(proj.testResults.failed.size == 1)
    assert(proj.testResults.passed.size == 0)

    //Repair the broken test, check there is one passing test
    writeToFile(
      new File(root, "tests/foo/BadTest.scala"), 
      """
      package foo
      import org.scalatest.FunSuite
      class BadTest extends FunSuite{
        test("test foo"){
          assert(1 === 1)
        }
      }
      """
    )
    proj.testFailingSuites
    assert(proj.testResults.failed.size == 0)
    assert(proj.testResults.passed.size == 1)

    //Run failed tests - should have no results at all
    proj.testFailingSuites
    assert(proj.testResults.failed.size == 0)
    assert(proj.testResults.passed.size == 0)

    //Run failed tests - should have no results at all
    proj.testFailingSuites
    assert(proj.testResults.failed.size == 0)
    assert(proj.testResults.passed.size == 0)

    //Run all tests - should have two passes
    proj.test
    assert(proj.testResults.failed.size == 0)
    assert(proj.testResults.passed.size == 2)

    proj.delete
  }
}
