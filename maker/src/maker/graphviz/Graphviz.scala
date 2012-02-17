package maker.graphviz

import maker.project.Project
import maker.os.Command
import maker.utils.os.OsUtils._
import maker.task.{CompileJavaSourceTask, ProjectAndTask}
import java.io.File

object GraphVizDiGrapher {
  val graphName = "Maker-Project-Graph"

  def mkGraph(name : String,  graphDef : String) = {
    val g = "digraph \\\"%s\\\" { graph[size=\\\"100.0, 100.0\\\"]; "
    (g + "{ node[ratio=compress size = \\\"100.0, 100.0\\\" nodesep=\\\"0.1\\\" ranksep=\\\"0.1\\\" fontname=Helvetica fontsize=8]; %s ;} ;}")
      .format(name, graphDef)
  }

  def makeDot(graph : List[(Project, List[Project])], showLibDirs : Boolean = false, showLibs : Boolean = false) : String = {
    def mkProjectDep(p1 : Project,  p2 : Project) =
      "{ node[fillcolor=yellow style=filled]; \\\"%s\\\"->\\\"%s\\\" ;}".format(p1.name, p2.name)

    def mkLibDirDep(p : Project,  d : File) =
      "\\\"%s\\\"->{ node[shape=box color=blue style=filled fillcolor=lightskyblue]; \\\"%s\\\" ;}".format(p.name, d.getPath)

    def mkLibFileDep(d : File, df : File) =
      "\\\"%s\\\"->{ node[color=none; shape=plaintext]; \\\"%s\\\" ;}".format(d.getPath, df.getName())

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
    val dot = mkGraph(graphName, g.distinct.mkString(" "))
    dot
  }
  
  def makeDotFromProjectAndTask(ps : List[(ProjectAndTask, List[ProjectAndTask])]) : String = {
    val allTimes = ps.distinct.flatMap(pt => pt._2.map(p => pt._1.lastRunTimeMs))
    val numberOfTasks = allTimes.size
    val avgTime = allTimes.sum / numberOfTasks
    def ptLabel(pt : ProjectAndTask) = {
      val size = pt.lastRunTimeMs.toDouble / avgTime
      def nodeAttrs = if (!pt.completed) " style=filled fillcolor=red" else if (pt.task == CompileJavaSourceTask) " style=filled fillcolor=lightskyblue" else ""
      "{ \\\"<%s> %s Took %dms\\\" [width=%f height=%f %s] }".format(pt.task, pt.project.name, pt.lastRunTimeMs, size*2.0, size, nodeAttrs)
    }
    val g = ps.distinct.flatMap(pt => pt._2.map(p => {
      "%s->%s".format(ptLabel(pt._1), ptLabel(p))
    }))
    val dot = mkGraph(graphName, g.distinct.mkString(" "))
    println("dot = " + dot)
    dot
  }
  
  def makeDotFromString(graph : List[(Project, List[String])]) : String = {
    val g = graph.distinct.flatMap(pd => pd._2.map(p =>
          "\\\"-Project-%s\\\"->\\\"%s\\\"".format(pd._1.name, p))).mkString(" ")
    val dot = mkGraph(graphName, g.distinct.mkString(" "))
    println("dot = " + dot)
    dot
  }
}

object GraphVizUtils {
  val DEFAULT_IMAGE_FORMAT = "png"
  val DEFAULT_TMP_FILE = new File("maker-gv-tmp." + DEFAULT_IMAGE_FORMAT)
  def removeGraphFile(file : File = DEFAULT_TMP_FILE) = {
    Command("rm", file.getAbsolutePath).exec()
    file
  }
  def createGraphFile(graphDef : String, file : File = DEFAULT_TMP_FILE) = {
    Command("/bin/sh", "-c", "echo \"" + graphDef + "\" | dot -T" + DEFAULT_IMAGE_FORMAT + " > " + file.getAbsolutePath).exec()
    file
  }

  def showGraph(graphDef : String, file : File = DEFAULT_TMP_FILE) {
    removeGraphFile(file)
    val f = createGraphFile(graphDef, file)
    if (isLinux)
      Command("xdg-open", f.getAbsolutePath).exec(true)
    else // assume OSX until we want to support other OSes such as windows
      Command("open", f.getAbsolutePath).exec()
  }
}
