package maker.graphviz

import maker.project.Project
import maker.os.Command
import java.io.File


object GraphVizDiGrapher {
  def makeDot(graph : List[(Project, List[Project])]) = {
    val g = graph.flatMap(pd => pd._2.map(x =>
          "\\\"Project-%s\\\"->\\\"Project-%s\\\"".format(pd._1.name, x.name))).mkString(" ")
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
    Command("open", f.getAbsolutePath).exec
  }
}
