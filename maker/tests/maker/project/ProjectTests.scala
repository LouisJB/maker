package maker.project
import org.scalatest.FunSuite
import java.io.File
import maker.utils.FileUtils._
import org.scalatest.BeforeAndAfterEach

class ProjectTests extends FunSuite with BeforeAndAfterEach{

  var proj : Project = _

  override def beforeEach{
    val root = tempDir("fred")
    proj = Project("foox", root, List(new File(root, "src")), Nil, new File(root, "out"), new File(root, "out-jar"))

    writeToFile(
      new File(root, "src/foo/Foo.scala"),
      """
      package foo
      case class Foo(x : Double){
      }
      """
    )
    writeToFile(
      new File(root, "src/foo/bar/Foo.scala"),
      """
      package foo.bar
      import foo.Foo

      case class Bar(x : Foo){
      }
      """
    )
  }

  test("Compilation makes class files"){
    proj.clean
    assert(proj.classFiles.size === 0)
    proj.compile
    assert(proj.classFiles.size > 0)
    proj.clean
    assert(proj.classFiles.size === 0)
  }

  test("Package makes jar"){
    proj.clean
    assert(!proj.outputJar.exists)
    proj.pack
    assert(proj.outputJar.exists)
    proj.clean
    assert(!proj.outputJar.exists)
  }

  override def afterEach(){
    proj.delete
  }
}
