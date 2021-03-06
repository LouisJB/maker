package maker.project

import org.scalatest.FunSuite
import java.io.File
import maker.utils.FileUtils._
import maker.project.Project._
import scalaz.Scalaz._
import maker.utils.Log
import org.apache.log4j.Level._

class ProjectTests extends FunSuite with TestUtils {

  val originalFooContent = 
    """
    package foo
    case class Foo(x : Double){
      val fred = 10
      def double() = x + x
    }
    """
  val originalBarContent = 
    """
    package foo.bar
    import foo.Foo

    case class Bar(x : Foo)
    """
  val originalBazContent = 
    """
    package foo
    case class Baz(y : Int)
    """

  def simpleProject(root : File) = {
    val proj : Project = makeTestProject("foox", root)
    val outputDir = file(root, "classes")
    val files = new {
      val fooSrc = new File(root, "src/foo/Foo.scala")
      val barSrc = new File(root, "src/foo/bar/Bar.scala")
      val bazSrc = new File(root, "src/foo/Baz.scala")
      def fooClass = new File(outputDir, "foo/Foo.class")
      def fooObject = new File(outputDir, "foo/Foo$.class")
      def barClass = new File(outputDir, "foo/bar/Bar.class")
      def barObject = new File(outputDir, "foo/bar/Bar$.class")
    }
    import files._
    proj.writeSrc("foo/Foo.scala", originalFooContent)
    //writeToFile(fooSrc, originalFooContent)
    writeToFile(barSrc, originalBarContent)
    writeToFile(bazSrc, originalBazContent)
    (proj, files)
  }

  test("Compilation makes class files, writes dependencies, and package makes jar"){
    withTempDir {
      dir ⇒ 
        val (proj, _) = simpleProject(dir)
        proj.clean
        assert(proj.classFiles.size === 0)
        proj.compile
        assert(proj.classFiles.size > 0)
        assert(!proj.outputArtifact.exists)
        proj.pack
        assert(proj.outputArtifact.exists)
        proj.clean
        assert(proj.classFiles.size === 0)
        assert(!proj.outputArtifact.exists)
    }
  }

  test("Compilation not done if signature unchanged"){
    withTempDir {
      dir ⇒ 
        val (proj, files) = simpleProject(dir)
        import files._
        proj.compile
        var compilationTime = proj.state.compilationTime.get
        sleepToNextSecond
        writeToFile(fooSrc, originalFooContent)
        proj.compile
        var changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
        assert(changedClassFiles === Set(fooClass, fooObject))
    }
  }

  test("Compilation is done if signature changed, but only on dependent classes"){
    withTempDir {
      dir ⇒ 
        val (proj, files) = simpleProject(dir)
        proj.compile
        import files._
        val compilationTime = proj.state.compilationTime.get
        sleepToNextSecond

        writeToFile(
          fooSrc,
          """
          package foo
          case class Foo(x : Double){
            val fred = 10
            def double() = x + x
            def newPublicMethod(z : Int) = z + z
          }
          """
        )
        proj.compile
        val changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
        assert(changedClassFiles === Set(fooClass, fooObject, barClass, barObject))
    }
  }

  test("Compilation of dependent classes is not done if signature of public method is unchanged"){
    withTempDir {
      dir ⇒ 
        val (proj, files) = simpleProject(dir)
        import files._
        proj.compile
        val compilationTime = proj.state.compilationTime.get
        sleepToNextSecond

        writeToFile(
          fooSrc,
          """
          package foo
          case class Foo(x : Double){
            val fred = 10
            def double() = x + x + x  //implementation changed
          }
          """
        )
        proj.compile
        val changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
        assert(changedClassFiles === Set(fooClass, fooObject))
    }
  }

  test("Compilation of dependent classes is not done if new private method is added"){
    withTempDir {
      dir ⇒ 
        val (proj, files) = simpleProject(dir)
        import files._
        proj.compile
        val compilationTime = proj.state.compilationTime.get
        sleepToNextSecond

        writeToFile(
          fooSrc,
          """
          package foo
          case class Foo(x : Double){
            val fred = 10
            def double() = x + x 
            private def treble() = x + x + x
          }
          """
        )
        proj.compile
        val changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
        assert(changedClassFiles === Set(fooClass, fooObject))
    }
  }

  test("Deletion of source file causes deletion of class files"){
    withTempDir {
      dir ⇒ 
        val (proj, files) = simpleProject(dir)
        import files._
        proj.compile
        Set(barClass, barObject) |> {
          s => assert((s & proj.classFiles) === s)
        }
        barSrc.delete
        proj.compile
        Set(barClass, barObject) |> {
          s => assert((s & proj.classFiles) === Set())
        }
    }
  }

  test("Test files have the correct dependencies"){
    withTempDir{
      dir ⇒ 
        val proj = makeTestProject("test-file-dependencies", dir)
        val fooSrc = file(dir, "src/foo/Foo.scala")
        val fooTest = file(dir, "tests/foo/FooTest.scala")
        writeToFile(
          fooSrc,
          """
            package foo
            case class Fooble(i : Int)
          """
        )
        writeToFile(
          fooTest,
          """
            package foo
            import org.scalatest.FunSuite
            class FooTest extends FunSuite{
              test("Foo constructor"){
                val foo = Fooble(10)
                assert(foo.i == 10)
              }
            }
          """
        )
        proj.testCompile
        val deps = proj.state.fileDependencies.sourceParentDependencies(Set(fooTest))
        assert(deps.contains(fooSrc))

    }
  }

  test("Generated class files are deleted before compilation of source"){
    withTempDir{
      dir ⇒ 
        val proj = makeTestProject("test-class-file-deletion", dir)
        val fooSrc = file(dir, "src/foo/Foo.scala")
        writeToFile(
          fooSrc,
          """
            package foo
            case class Fred(i : Int)
            case class Ginger(i : Int)
          """
        )
        proj.compile
        val fredClass = new File(proj.outputDir, "foo/Fred.class")
        val gingerClass = new File(proj.outputDir, "foo/Ginger.class")
        assert(fredClass.exists && gingerClass.exists)

        sleepToNextSecond
        writeToFile(
          fooSrc,
          """
            package foo
            case class Fred(i : Int)
            //case class Ginger(i : Int)
          """
        )
        proj.compile
        assert(fredClass.exists, "Fred should still exist")
        assert(!gingerClass.exists, "Ginger should not exist")
    }
  }

  override def sleepToNextSecond{
    Thread.sleep(1100)
  }


}
