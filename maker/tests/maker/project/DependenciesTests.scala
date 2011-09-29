package maker.project

import org.scalatest.FunSuite
import java.io.File
import maker.utils.FileUtils._

class DependenciesTests extends FunSuite{

  test("Can persist and read back dependencies"){
    withTempFile {
      file : File => 
        val deps = new Dependencies(
          Map(
            new File("foo") -> Set(new File("bar"), new File("fred/mike")),
            new File("goo") -> Set(new File("car"), new File("bred/mike"))
          ),
          file
        )
        deps.persist()
        val deps2 = Dependencies(file)
        assert(deps === deps2)
    }
  }
}