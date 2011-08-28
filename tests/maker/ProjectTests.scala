package maker

import org.scalatest.FunSuite
import java.io.File

class ProjectTests extends FunSuite{
  test("Copilation makes class files"){

    val root = new File("tests/projects/simple-project")
    val proj = Project(root, List(new File(root, "src")), Nil, new File(root, "out"))
    proj.clean
    assert(proj.classFiles.size === 0)
    proj.compile
    assert(proj.classFiles.size > 0)
    proj.clean
  }
}
