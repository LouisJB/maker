package maker.task

import org.scalatest.FunSuite
import scala.xml.XML
import scala.xml.MetaData
import scala.xml.Attribute
import java.io.File
import maker.project.Project
import maker.utils.FileUtils._
import maker.utils.Log
import org.apache.log4j.Level._


class ScalatestResultsTests extends FunSuite{
  def metadataToMap(md : MetaData, acc : Map[String, String] = Map[String, String]()) : Map[String, String] = {
    md match {
      case scala.xml.Null ⇒ acc
      case a : Attribute ⇒ metadataToMap(md.next, acc ++ Map(a.key → a.value.toString))
    }
  }

  def makeProject(name : String, root : File) = {
    Project(
      name, 
      root, 
      List(new File(root, "src")), 
      List(new File(root, "tests")), 
      libDirs=List(new File(".maker/lib"))
    ).withTaskOutputSuppressed
  }
  test("Errors are correctly counted"){
    withTempDir{
      dir ⇒
        val proj = makeProject("Error checker", dir)
        val fooTestContent = 
          """
          package foo
          import org.scalatest.FunSuite
          class FooTest extends FunSuite{
            test("test 1 == 1"){
              assert(1 === 1)
            }
            test("test 5 == 5"){
              assert(1 === 1)
            }
            test("test 1 == 2"){
              assert(1 === 2)
            }
          }
          """
        writeToFile(new File(proj.root, "tests/foo/FooTest.scala"), fooTestContent)
        proj.test
        val results = ScalatestResults(proj)
        assert(proj.testResults.passed.size === 2)
        assert(proj.testResults.failed.size === 1)
      }

    
  }
}
