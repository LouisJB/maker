package maker.project

import org.scalatest.FunSuite
import java.io.File
import maker.utils.FileUtils._
import maker._
import maker.project._
import maker.project.Project._
import scalaz.Scalaz._

class ProjectTests extends FunSuite {

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

  private def sleepToNextSecond{
    Thread.sleep(1100)
  }


}
