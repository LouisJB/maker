package maker.utils.ivy

import java.io.File
import scala.xml._
import maker.utils._
import maker.utils.maven._
import maker.utils.GroupId._

/**
 * Read in raw ivy xml files for module dependencies
 */
object IvyReader {
  def readIvyDependenciesFromFile(file : File) : List[DependencyLib] = {
    try {
      val ivyXml = XML.loadFile(file)
      val deps = (ivyXml \\ "dependency")
      deps.map(d => {
        val name = (d \\ "@name").toString
        val rev = (d \\ "@rev").toString
        val org = (d \\ "@org").toString
        DependencyLib(name, org % name % rev, "compile")
      }).toList
    }
    catch {
      case _ : java.io.FileNotFoundException => Nil // assume no file = no deps, for now
      case th : Throwable => {
        Log.error("Error reading XML file " + file.getAbsolutePath, th)
        Nil
      }
    }
  }

  def readIvyResolversFromFile(file : File) : List[MavenRepository] = {
    try {
      val ivyXml = XML.loadFile(file)
      val repos = (ivyXml \\ "ibiblio")
      repos.map(r => {
        val id = (r \\ "@name").toString
        val url = (r \\ "@root").toString
        MavenRepository(id, id, url, "default")
      }).toList
    }
    catch {
      case _ : java.io.FileNotFoundException => Nil // assume no file = no deps, for now
      case th : Throwable => {
        Log.error("Error reading XML file " + file.getAbsolutePath, th)
        Nil
      }
    }
  }
}

