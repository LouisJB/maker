package plugin

import org.scalatest.{BeforeAndAfterEach, FunSuite}
import maker.utils.FileUtils._
import java.io.File
import maker.project.Project._
import maker.project.Project

class WriteSignaturesTests extends FunSuite with BeforeAndAfterEach{
  val root = tempDir("fred")
  val fooSrc = new File(root, "src/foo/Foo.scala")
  var proj : Project = _
  val fooContent =
    """
    package foo.bar
    case class Foo(x : Double){
      def fab[A](i : Int, j : A) = {
        i + i
      }
      private def fabble = 20
      val z = 20
    }
    object Foo{
      class Bar{
        def foobarmethod(i : Int) = i + i
      }
    }
    """
  override def afterEach(){
    proj.delete
  }

  test("what can we get"){
    proj = Project(
      "foox", 
      root, 
      List(new File(root, "src")), 
      Nil, 
      Nil
    )

    writeToFile(fooSrc, fooContent)
    println("There are " + proj.srcFiles.size + " src filed")
    proj.compile
    println("There are " + proj.classFiles.size + " classes")
    println("Finished")

  }
}
