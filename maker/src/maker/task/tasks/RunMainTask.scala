package maker.task.tasks

import annotation.tailrec
import maker.project.Project
import maker.task.{ProjectAndTask, Task, TaskFailed}
import maker.utils.Log
import maker.utils.FileUtils._
import java.io.PrintWriter
import maker.utils.TeeToFileOutputStream
import maker.utils.os.CommandOutputHandler
import maker.utils.os.ScalaCommand


/**
 * run a class main in a separate JVM instance (but currently synchronously to maker repl)
 */
case object RunMainTask extends Task {
  val runLogFile = file("runlog.out")
  def exec(implicit project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    parameters.get("mainClassName") match {
      case Some(name) => {
        Log.info("running main in class " + name + " of project " + project)
        val className = parameters("mainClassName")
        val opts: List[String] = parameters.get("opts").map(_.split("\\|").toList).getOrElse(Nil)
        val mainArgs: List[String] = parameters.get("args").map(_.split("\\|").toList).getOrElse(Nil)

        val writer = new PrintWriter(new TeeToFileOutputStream(runLogFile))
        val cmd = ScalaCommand(
          new CommandOutputHandler(Some(writer)).withSavedOutput,
          project.props.JavaHome() + "/bin/java",
          opts,
          project.runClasspath,
          className,
          mainArgs : _*
        )

        writeToFile(file("runcmd.sh"), "#!/bin/bash\n" + cmd.asString)
        Log.info("Running, press ctrl-] to terminate running process...")

        val procHandle = cmd.execAsync()
        @tailrec
        def checkRunning(): Either[TaskFailed, AnyRef] = {
          if (!procHandle._2.isSet) {
            Thread.sleep(1000)
            if (System.in.available > 0 && System.in.read == Task.termChar) {
              Log.info("Terminating: " + className)
              procHandle._1.destroy()
              Log.info("Terminated process for runMain of class : " + className)
              Right("User terminated")
            }
            else checkRunning()
          }
          else {
            procHandle._2() match {
              case 0 => Right("Ok")
              case code => Left(TaskFailed(ProjectAndTask(project, this), "Run Main failed in " + project + ", " + cmd.savedOutput))
            }
          }
        }
        checkRunning()
      }
      case None =>
        Log.info("No class, nothing to do")
        Left(TaskFailed(ProjectAndTask(project, this), "No class specified"))
    }
  }
}
