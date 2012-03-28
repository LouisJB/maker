package maker.task

import maker.project.Project
import maker.utils.Log
import maker.utils.FileUtils._
import maker.os.Command
import scala.actors.Futures._

/**
 * run a class main in a separate JVM instance
 */
case object RunMainTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    parameters.get("mainClassName") match {
      case Some(name) => {
        Log.info("running main in class " + name + " of project " + project)
        val className = parameters("mainClassName")
        val opts : List[String] = parameters.get("opts").map(_.split(",").toList).getOrElse(Nil)
        val mainArgs : List[String] = parameters.get("args").map(_.split(",").toList).getOrElse(Nil)
        val args = List(
          project.props.JavaHome() + "/bin/java",
          "-Dscala.usejavacp=true",
          "-classpath",
          project.runClasspath + ":" + project.scalaLibs.mkString(":")) :::
          opts ::: (
          "scala.tools.nsc.MainGenericRunner" ::
          className :: mainArgs)

        var proc : Process = null
        val r = future {
          val cmd = Command(file("runlog.out"), args: _*)
          Log.info("Running, press enter to terminate process...")
          proc = cmd.startProc()
          cmd.waitOnProc(proc) match {
            case (0, _) => Right("Ok")
            case (code, msg) => Left(TaskFailed(ProjectAndTask(project, this), "Run Main failed in " + project + ", " + msg))
          }
        }
        def checkRunning() : Either[TaskFailed, AnyRef] = {
          if (!r.isSet) {
            Thread.sleep(1000)
            if (System.in.available > 0 && System.in.read == ('\n').toInt) {
              Log.info("Terminating: " + className)
              proc.destroy()
              Log.info("Terminated process for runMain of class : " + className)
              Right("User terminated")
            }
            else checkRunning()
          }
          else r()
        }
        checkRunning()
      }
      case None =>
        Log.info("No class, nothing to do")
        Left(TaskFailed(ProjectAndTask(project, this), "No class specified"))
    }
  }
}
