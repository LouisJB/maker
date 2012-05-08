package maker.task
import maker.utils.FileUtils._
import maker.project.Project
import scala.xml.XML
import scala.xml.MetaData
import scala.xml.Attribute
import maker.utils.Log

case class TestResult(suite : String, testName : String, time : Double, error : Option[String]){
  val passed = ! error.isDefined
}

case class ScalatestResults(results : Set[TestResult]) {
  def ++ (rhs : ScalatestResults) = ScalatestResults(results ++ rhs.results)
  lazy val (passed, failed) = results.partition(_.passed)
}


object ScalatestResults extends App{
  def metadataToMap(md : MetaData, acc : Map[String, String] = Map[String, String]()) : Map[String, String] = {
    md match {
      case scala.xml.Null ⇒ acc
      case a : Attribute ⇒ metadataToMap(md.next, acc ++ Map(a.key → a.value.toString))
    }
  }
  def apply(project : Project) : ScalatestResults = {
    val xmlFiles = findFilesWithExtension("xml", project.testResultsDir)
    val xmlNodes = xmlFiles.map(XML.loadFile).flatten.toSet
    val results = xmlNodes.map{
      node ⇒ 
        val attributes = metadataToMap(node.attributes)
        Log.info(attributes)
        val failures = (node \\ "failure").toList.map(_.toString)
        assert(failures.size <= 1)
        TestResult(
          suite = attributes("classname"), 
          testName = attributes("name"),
          time = attributes("time").toDouble,
          error = failures.headOption
        )
    }
    ScalatestResults(results)
  }
}
