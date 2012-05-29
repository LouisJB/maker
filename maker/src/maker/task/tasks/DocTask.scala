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
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    val runDocLogFile = file("rundoc.out")
    Log.info("running scala-doc gen for project " + project)
    val docPath = file(project.root, "docs")
    if (!docPath.exists) docPath.mkdir
    Log.debug("docPath " + docPath.getAbsolutePath)
    val writer = new PrintWriter(new TeeToFileOutputStream(runDocLogFile))

    val cmd = ScalaDocCmd(
      new CommandOutputHandler(Some(writer)).withSavedOutput,
      docPath,
      project.props.javaExec,
      project.compilationClasspath,
      Nil,
      project.srcFiles().toSeq : _*)

    writeToFile(file(project.root, "doccmd.sh"), "#!/bin/bash\n" + cmd.asString)

    cmd.exec() match {
      case 0 => Right("Ok")
      case _ => Left(TaskFailed(ProjectAndTask(project, this), cmd.savedOutput))
    }
  }
}
