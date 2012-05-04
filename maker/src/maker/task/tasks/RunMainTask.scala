package maker.task.tasks

import maker.project.Project
import maker.utils.Log
import maker.utils.FileUtils._
import maker.os.Command
import annotation.tailrec
import maker.task.{ProjectAndTask, TaskFailed, Task}

/**
 * run a class main in a separate JVM instance (but currently synchronously to maker repl)
 */
case object RunMainTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    parameters.get("mainClassName") match {
      case Some(name) => {
        Log.info("running main in class " + name + " of project " + project)
        val className = parameters("mainClassName")
        val opts: List[String] = parameters.get("opts").map(_.split("\\|").toList).getOrElse(Nil)
        val mainArgs: List[String] = parameters.get("args").map(_.split("\\|").toList).getOrElse(Nil)
        val args = List(
          project.props.JavaHome() + "/bin/java",
          "-Dscala.usejavacp=true",
          "-classpath",
          project.runClasspath + ":" + project.scalaLibs.mkString(":")) :::
          opts ::: (className :: mainArgs)

        val cmd = Command(file("runlog.out"), args: _*)
        writeToFile(file("runcmd.sh"), "#!/bin/bash\n" + cmd.asString)
        Log.info("Running, press ctrl-] to terminate running process...")

        val procHandle = cmd.execProc()
        @tailrec
        def checkRunning(): Either[TaskFailed, AnyRef] = {
          if (!procHandle._2.isSet) {
            Thread.sleep(1000)
            if (System.in.available > 0 && System.in.read == 29 /* ctrl-] */) {
              Log.info("Terminating: " + className)
              procHandle._1.destroy()
              Log.info("Terminated process for runMain of class : " + className)
              Right("User terminated")
            }
            else checkRunning()
          }
          else {
            procHandle._2() match {
              case (0, _) => Right("Ok")
              case (code, msg) => Left(TaskFailed(ProjectAndTask(project, this), "Run Main failed in " + project + ", " + msg))
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
