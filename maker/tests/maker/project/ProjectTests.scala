package maker.project

import org.scalatest.FunSuite
import java.io.File
import _root_.maker.utils.FileUtils._
import _root_.maker._
import _root_.maker.project._
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

  def simpleProject = {
    val root = tempDir("fred")
    val outputDir = file(root, "classes")
    val proj = Project(
      "foox", 
      root, 
      List(new File(root, "src")), 
      Nil,
      libDirs=List(new File(".maker/lib"))
    ) 
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
    writeToFile(fooSrc, originalFooContent)
    writeToFile(barSrc, originalBarContent)
    writeToFile(bazSrc, originalBazContent)
    (proj, files)
  }

  test("Compilation makes class files, writes dependencies, and package makes jar"){
    val (proj, _) = simpleProject
    proj.clean
    assert(proj.classFiles.size === 0)
    proj.compile
    assert(proj.classFiles.size > 0)
    assert(!proj.outputJar.exists)
    proj.pack
    assert(proj.outputJar.exists)
    proj.clean
    assert(proj.classFiles.size === 0)
    assert(!proj.outputJar.exists)
    proj.delete
  }

  test("Compilation not done if signature unchanged"){
    val (proj, files) = simpleProject
    import files._
    proj.compile
    var compilationTime = proj.state.compilationTime.get
    sleepToNextSecond
    writeToFile(fooSrc, originalFooContent)
    proj.compile
    var changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
    assert(changedClassFiles === Set(fooClass, fooObject))
    proj.delete
  }

  test("Compilation is done if signature changed, but only on dependent classes"){
    val (proj, files) = simpleProject
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
    proj.delete
  }

  test("Compilation of dependent classes is not done if signature of public method is unchanged"){
    val (proj, files) = simpleProject
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
    proj.delete
  }

  test("Compilation of dependent classes is not done if new private method is added"){
    val (proj, files) = simpleProject
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
    proj.delete
  }

  test("Deletion of source file causes deletion of class files"){
    val (proj, files) = simpleProject
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

  private def sleepToNextSecond{
    Thread.sleep(1100)
  }


}
