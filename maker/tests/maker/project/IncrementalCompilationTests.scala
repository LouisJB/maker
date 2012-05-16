package maker.project

import org.scalatest.FunSuite
import java.io.File
import maker.utils.FileUtils._
import maker.project.Project._
import maker.task.{TaskFailed, BuildResult}
import org.scalatest.Assertions._

trait TestUtils {
  def sleepToNextSecond = Thread.sleep(1100)

  def checkCompilationOk(br : BuildResult[_]) = br.res match {
    case Left(TaskFailed(_, reason)) => fail("compilation failed when should have succeeded")
    case _ => // all ok
  }
  def checkCompilationFailed(br : BuildResult[_]) = br.res match {
    case Left(TaskFailed(_, reason)) => // all ok
    case _ => fail("compilation succeeded when should have failed")
  }
}

class IncrementalCompilationTests extends FunSuite with TestUtils {

  val originalFooSrcs =
    """
    package foo
    trait Foo {
      def bar : Int
    }
    """
  val fooImplSrcs =
    """
    package foo
    class Bar extends Foo {
      def bar : Int = 1
    }
    """
  val newFooSrcs =
    """
    package foo
    trait Foo {
      def bar : String
    }
    """
  val newFooImplSrcs =
      """
      package foo
      class Bar extends Foo {
        def bar : String = "1"
      }
      """
  def simpleProject = {

    val root = tempDir("fred")
    val proj : Project = makeTestProject("foox", root)
    val outputDir = file(root, "classes")

    val files = new {
      val traitSrcFile = "src/foo/Foo.scala"
      val implSrcFile = "src/foo/bar/Bar.scala"
      def fooClass = new File(outputDir, "foo/Foo.class")
      def barClass = new File(outputDir, "foo/Bar.class")
    }
    import files._

    proj.writeSrc(traitSrcFile, originalFooSrcs)
    proj.writeSrc(implSrcFile, fooImplSrcs)

    (proj, files)
  }

  test("Incremental compilation recompiles implementation of changed interfaces"){
    val (proj, files) = simpleProject
    import files._
    proj.clean
    assert(proj.classFiles.size === 0)

    // check prj initially compiles ok
    checkCompilationOk(proj.compile)

    // now update the base trait to invalidate implementations, check it fails
    val compilationTime = proj.state.compilationTime.get
    sleepToNextSecond
    proj.writeSrc(traitSrcFile, newFooSrcs)

    checkCompilationFailed(proj.compile)

    var changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
    assert(changedClassFiles === Set(fooClass))

    // now put a matching implementation in and all should be ok again
    sleepToNextSecond
    proj.writeSrc(implSrcFile, newFooImplSrcs)

    checkCompilationOk(proj.compile)

    changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
    assert(changedClassFiles === Set(fooClass, barClass))

    proj.delete
  }
}
