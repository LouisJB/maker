package maker.graphviz

import maker.project.Project
import maker.os.Command
import maker.utils.os.OsUtils._
import java.io.File


object GraphVizDiGrapher {
  def makeDot(graph : List[(Project, List[Project])], showLibDirs : Boolean = false, showLibs : Boolean = false) : String = {
    
    def mkProjectDep(p1 : Project,  p2 : Project) =
      "{ node[fillcolor=yellow style=filled]; \\\"%s\\\"->\\\"%s\\\" ;}".format(p1.name, p2.name)

    def mkLibDirDep(p : Project,  d : File) =
      "\\\"%s\\\"->{ node[shape=box color=blue style=filled fillcolor=lightskyblue]; \\\"%s\\\" ;}".format(p.name, d.getPath)
      //"\\\"%s\\\"->{ node [shape=polygon sides=4 skew=0.2 color=red];\\\"%s\\\";}".format(p.name, d.getPath)

    def mkLibFileDep(d : File, df : File) =
      "\\\"%s\\\"->{ node[color=none; shape=plaintext]; \\\"%s\\\" ;}".format(d.getName, df.getName())

    def mkGraph(name : String,  graphDef : String) = {
      val g = "digraph \\\"%s\\\" { graph[size=\\\"10.25, 7.75\\\"]; "
      (g + "{ node[ratio=compress size = \\\"10.0, 10.0\\\" nodesep=\\\"0.1\\\" ranksep=\\\"0.1\\\" fontname=Helvetica fontsize=8]; %s ;} ;}")
        .format(name, graphDef)
    }

    // take the distinct set here as we want to ignore duplicated paths caused by indirect dependencies
    val g = graph.distinct.flatMap{ case (proj, deps) => deps.flatMap(pd => {
        mkProjectDep(proj, pd) :: { if (showLibDirs)
              proj.libDirs.flatMap(libDir => mkLibDirDep(proj, libDir) :: { if (showLibs)
                Option(libDir.listFiles()).map(_.toList).getOrElse(Nil).map(x => mkLibFileDep(libDir, x))
              else Nil
            })
          else Nil
        }
      })
    }
    val dot = mkGraph("Maker-Project-Graph", g.distinct.mkString(" "))
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
