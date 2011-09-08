package maker.project
import org.scalatest.FunSuite
import java.io.File

class ProjectTests extends FunSuite{
  val root = new File("tests/projects/simple-project")
  val proj = Project("foo", root, List(new File(root, "src")), Nil, new File(root, "out"), new File(root, "out-jar"))

  test("Compilation makes class files"){
    proj.clean
    assert(proj.classFiles.size === 0)
    proj.compile
    assert(proj.classFiles.size > 0)
    proj.clean
  }

  test("Package makes jar"){
    proj.clean
    assert(!proj.outputJar.exists)
    proj.pack
    assert(proj.outputJar.exists)
  }
}
