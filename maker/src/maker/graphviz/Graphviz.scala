package maker.graphviz

import maker.project.Project
import maker.os.Command
import maker.utils.os.OsUtils._
import java.io.File


object GraphVizDiGrapher {
  def makeDot(graph : List[(Project, List[Project])], showLibDirs : Boolean = false, showLibs : Boolean = false) : String = {
    // take the distinct set here as we want to ignore duplicated paths caused by indirect dependencies
    val g = graph.distinct.flatMap{ case (proj, deps) => deps.flatMap(pd => {
        ("\\\"Project-%s\\\"->\\\"Project-%s\\\"".format(proj.name, pd.name)) :: { if (showLibDirs) {
              proj.libDirs.flatMap(libDir => "\\\"Project-%s\\\"->\\\"LibDir-%s\\\"".format(proj.name, libDir.getPath) :: { if (showLibs) {
                Option(libDir.listFiles()).map(_.toList.map(_.getName)).getOrElse(Nil).map(x => "\\\"LibDir-%s\\\"->\\\"LibFile-%s\\\"".format(libDir.getName, x))
              }
              else Nil
            })
          }
          else Nil
        }
      })
    }
    val dot = "digraph G { %s }".format(g.mkString(" "))
    println("dot = " + dot)
    dot
  }
  
  def makeDotFromString(graph : List[(Project, List[String])]) : String = {
    // take the distinct set here as we want to ignore duplicated paths caused by indirect dependencies
    val g = graph.distinct.flatMap(pd => pd._2.map(p =>
          "\\\"-Project-%s\\\"->\\\"%s\\\"".format(pd._1.name, p))).mkString(" ")
    val dot = "digraph G { %s }".format(g)
    println("dot = " + dot)
    dot
  }
}

object GraphVizUtils {
  val DEFAULT_IMAGE_FORMAT = "png"
  val DEFAULT_TMP_FILE = new File("maker-gv-tmp." + DEFAULT_IMAGE_FORMAT)
  def createDotFile(graphDef : String, file : java.io.File = DEFAULT_TMP_FILE) = {
    Command("/bin/sh", "-c", "echo \"" + graphDef + "\" | dot -T" + DEFAULT_IMAGE_FORMAT + " > " + file.getAbsolutePath).exec
    file
  }

  def showGraph(graphDef : String, file : java.io.File = DEFAULT_TMP_FILE) {
    val f = createDotFile(graphDef, file)
    if (isLinux)
      Command("xdg-open", f.getAbsolutePath).exec
    else // assume OSX unless we want to support other OSes such as windows
      Command("open", f.getAbsolutePath).exec
  }
}
