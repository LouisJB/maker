package maker.graphviz

import maker.project.Project
import maker.os.Command
import maker.utils.os.OsUtils._
import maker.task.{CompileJavaSourceTask, ProjectAndTask}
import java.io.File

object GraphVizDiGrapher {
  val graphName = "Maker-Project-Graph"
  val defaultFont = "fontname=Helvetica fontsize=8"

  def mkGraph(name : String,  graphDef : String) = {
    val g = "digraph \\\"%s\\\" { graph[size=\\\"100.0, 100.0\\\"]; "
    (g + "{ node[ratio=compress size = \\\"100.0, 100.0\\\" nodesep=\\\"0.1\\\" ranksep=\\\"0.1\\\" %s]; %s } ;}")
      .format(name, defaultFont, graphDef)
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

  /**
   * makes simple graphviz digraph definition string from a
   *   ProjectAndTask tree (list of (parent -> list of children))
   */
  def makeDotFromProjectAndTask(ps : List[(ProjectAndTask, List[ProjectAndTask])]) : String = {
    val pts = ps.distinct
    val allTimes = pts.head._1.runTimeMs :: pts.flatMap(pt => pt._2.map(p => pt._1.runTimeMs))
    println("pts = " + pts + " allTimes = " + allTimes)
    val numberOfTasks = allTimes.size
    val avgTime = allTimes.sum / numberOfTasks
    def mkLabel(pt : ProjectAndTask) = {
      val size = pt.runTimeMs.toDouble / avgTime
      val nodeAttrs = if (!pt.completed) " style=filled fillcolor=red" else if (pt.task == CompileJavaSourceTask) " style=filled fillcolor=lightskyblue" else ""
      "{ \\\"<%s> %s (%d) Took %dms\\\" [width=%f height=%f %s] }"
        .format(pt.task, pt.project.name, pt.roundNo, pt.runTimeMs, size*1.5, size, nodeAttrs)
    }
    val g = pts match {
      case singleProjTask :: Nil => {
        List("%s".format(mkLabel(singleProjTask._1)))
      }
      case _ => pts.flatMap(pt => {
        import math._
        val criticalPathFinishingTime = (0L /: pt._2.map(_.finishingTime))(max)

        def mkArrowAttrs(pt : ProjectAndTask) =
          if (pt.finishingTime >= criticalPathFinishingTime) "[color=red]"
          else "[%s label=\\\"float=%sms\\\"]".format(defaultFont, (criticalPathFinishingTime - pt.finishingTime) / 1000000)

        pt._2.map(pdt =>
          "%s->%s %s".format(mkLabel(pt._1), mkLabel(pdt), mkArrowAttrs(pdt)))
      })
    }

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
  def imageBaseName = "maker-gv-tmp"
  def defaultImageFile = new File(imageBaseName + "." + DEFAULT_IMAGE_FORMAT)

  def removeGraphFile(file : File = defaultImageFile) = {
    file.delete()
    file
  }

  def createGraphFile(graphDef : String, file : File = defaultImageFile) = {
    Command("/bin/sh", "-c", "echo \"" + graphDef + "\" | dot -T" + DEFAULT_IMAGE_FORMAT + " > " + file.getAbsolutePath).exec()
    file
  }

  def showGraph(graphDef : String, file : File = defaultImageFile) =
    showImage(createGraphFile(graphDef, removeGraphFile(file)))

  def showImage(f : File) = {
    if (isLinux)
      Command("xdg-open", f.getAbsolutePath).exec(true)
    else // assume OSX until we want to support other OSes such as windows
      Command("open", f.getAbsolutePath).exec()
    f
  }
}
