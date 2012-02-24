package maker.utils.ivy

import java.io.File
import scala.io._
import scala.xml._
import maker.utils._

/**
 * Read in raw ivy xml files for module dependencies
 */
object IvyReader {
  def readIvyDependenciesFromFile(file : File) : List[DependencyLib] = {
    try {
      val ivyXml = XML.loadFile(file)
      val deps = (ivyXml \\ "dependency")
      deps.map(d => DependencyLib((d \\ "@name").toString, (d \\ "@rev").toString, (d \\ "@org").toString)).toList
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
