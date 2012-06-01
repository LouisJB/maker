package maker.task.tasks

import maker.project.Project
import maker.utils.FileUtils._
import java.io.PrintWriter
import maker.utils.{TeeToFileOutputStream, Log}
import maker.utils.os.{ScalaDocCmd, CommandOutputHandler}
import maker.task.{ProjectAndTask, TaskFailed, Task}


/** Doc generation task - produces scaladocs from project sources
  *
  * Outputs scala-docs per module in the "docs" sub-dir of the project root dir
  */
case object DocTask extends Task {
  def exec(implicit project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {

    val aggregate = parameters.getOrElse("aggregate", "false").toBoolean
    val runDocLogFile = file("rundoc.out")

    log("running scala-doc gen for project " + project)

    val writer = new PrintWriter(new TeeToFileOutputStream(runDocLogFile))

    val projects = if (aggregate) project.projectAndDescendents else project :: Nil
    val (classpath, inputFiles) = (
      projects.map(_.compilationClasspath).mkString(":"),
      projects.flatMap(_.srcFiles()))

    Log.debug("input files " + inputFiles)
    Log.debug("times " + (lastModifiedFileTime(inputFiles), lastModifiedFileTime(List(project.docRoot)).toString))

    if (aggregate || !project.docRoot.exists || lastModifiedFileTime(inputFiles) > lastModifiedFileTime(List(project.docRoot))) {
      Log.debug("generating doc for project " + project.toString)
      if (!project.docRoot.exists) project.docRoot.mkdirs
      val cmd = ScalaDocCmd(
        new CommandOutputHandler(Some(writer)).withSavedOutput,
        project.docRoot,
        project.props.javaExec,
        classpath,
        Nil,
        inputFiles.toSeq : _*)

      writeToFile(file(project.root, "doccmd.sh"), "#!/bin/bash\n" + cmd.asString)

      cmd.exec() match {
        case 0 => Right("Ok")
        case _ => Left(TaskFailed(ProjectAndTask(project, this), cmd.savedOutput))
      }
    }
    else {
      Log.debug("not generating doc for project " + project.toString)
      Right("Ok (nothing to do)")
    }
  }
}
