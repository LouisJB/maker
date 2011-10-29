package plugin

import org.scalatest.FunSuite
import org.scalatest.Assertions._
import maker.utils.FileUtils._
import java.io.{BufferedWriter, File}


class ProjectSignaturesTests extends FunSuite{

  test("ClassSignature toString round trip"){
    val klasses = List(
      ClassSignature(List("foo", "bar"), List("Fred", "Mike")),
      ClassSignature(List(), List("Mike")),
      ClassSignature(List(), List())
    )
    for (klass <- klasses){
      val klass2 = ClassSignature(klass.toString)
      assert(klass === klass2)
    }
  }

  test("MethodSignature toString round trip"){
    val List(c1, c2, c3) = List(
      ClassSignature(List("foo", "bar"), List("Fred", "Mike")),
      ClassSignature(List(), List("Mike")),
      ClassSignature(List(), List())
    )
    val methods = List(
      MethodSignature("Fred", c1, List(c2, c3), c3),
      MethodSignature("Mike", c3, List(c2, c3), c1)
    )
    for (m <- methods){
      val m2 = MethodSignature(m.toString)
      assert(m === m2)
    }
  }

  test ("ProjectSignature toString round trip"){
    val List(c1, c2, c3, c4, c5) = List(
      ClassSignature(List("foo", "bar"), List("Fred", "Mike")),
      ClassSignature(List(), List("Mike")),
      ClassSignature(List("bax", "fuz"), List("Bill", "George")),
      ClassSignature(List(), List()),
      ClassSignature(List("bob"), List("A", "B"))
    )

    val List(file1, file2) = List(new File("fred/Mike.scala"), new File("Foo.scala"))
    val sigs = ProjectSignatures()
    def testSigs(){
      withTempFile{tempFile : File =>
        sigs.persist(tempFile)
        val sigs2 = ProjectSignatures(tempFile)
        assert(sigs == sigs2)
      }
    }
    sigs += (file1, c1, MethodSignature("Foo", c1, List(c2, c3), c4))
    testSigs()
    sigs += (file1, c2, MethodSignature("Bar", c1, List(c2, c3, c1), c2))
    testSigs()
    sigs += (file2, c3, MethodSignature("Baz", c2, List(c1, c1, c1), c1))
    testSigs()
  }
}