package maker.project
import org.scalatest.FunSuite
import java.io.File
import maker.utils.FileUtils._
import org.scalatest.BeforeAndAfterEach

class ProjectTests extends FunSuite with BeforeAndAfterEach{

  val root = tempDir("fred")
  var proj : Project = _
  val fooSrc = new File(root, "src/foo/Foo.scala")
  val barSrc = new File(root, "src/foo/bar/Bar.scala")
  val bazSrc = new File(root, "src/foo/Baz.scala")
  def fooClass = new File(proj.outputDir, "foo/Foo.class")
  def fooObject = new File(proj.outputDir, "foo/Foo$.class")
  def barClass = new File(proj.outputDir, "foo/bar/Bar.class")
  def barObject = new File(proj.outputDir, "foo/bar/Bar$.class")

  val originalFooContent = 
    """
    package foo
    case class Foo(x : Double)
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
  def writeStandardProject{
    proj = Project("foox", root, List(new File(root, "src")), Nil, new File(root, "out"), new File(root, "out-jar"))

    writeToFile(fooSrc, originalFooContent)
    writeToFile(barSrc, originalBarContent)
    writeToFile(bazSrc, originalBazContent)
  }

  override def beforeEach(){
    writeStandardProject
  }

  override def afterEach(){
    proj.delete
  }

  test("Compilation makes class files, writes dependencies, and package makes jar"){
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
  }


  test("Compilation not done if signature unchanged"){
    proj.compile
    val compilationTime = proj.compilationTime.get

    Thread.sleep(1100)

    writeToFile(fooSrc, originalFooContent)
    proj.compile
    val changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
    assert(changedClassFiles == Set(fooClass, fooObject))
  }

//  test("Compilation is done if signature changed, but only on dependent classes"){
//    proj.compile
//    val compilationTime = proj.compilationTime.get
//    Thread.sleep(1100)
//
//    writeToFile(
//      fooSrc,
//      """
//        package foo
//        case class Foo(x : Double){
//          def newPublicMethod(z : Int) = z + z
//        }
//      """
//    )
//    proj.compile
//    val changedClassFiles = proj.classFiles.filter(_.lastModified > compilationTime)
//    assert(changedClassFiles == Set(fooClass, fooObject, barClass, barObject))
//  }
}
