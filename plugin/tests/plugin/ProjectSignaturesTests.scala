package plugin

import org.scalatest.FunSuite
import maker.utils.FileUtils._
import java.io.File
import utils.ProjectSignatures

class ProjectSignaturesTests extends FunSuite {

  test ("ProjectSignature toString round trip") {
    val List(c1, c2, c3, c4, c5) = List(
      "foo", "bar", "baz", "fux", "fob"
    )

    withTempFile{tempFile : File =>
      val List(file1, file2) = List(new File("fred/Mike.scala"), new File("Foo.scala"))
      val sigs = ProjectSignatures(tempFile)
      def testSigs(){
          sigs.persist()
          val sigs2 = ProjectSignatures(tempFile)
          assert(sigs === sigs2)
      }
      testSigs()
      sigs += (file1, Set(c1, c2, c3))
      testSigs()
      sigs += (file1, Set(c2, c4))
      testSigs()
      sigs += (file2, Set(c3, c5))
      testSigs()
    }
  }
}
