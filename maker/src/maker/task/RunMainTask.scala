package maker.task

import maker.project.Project
import maker.utils.Log
import maker.os.Command

/**
 * run a class main in a separate JVM instance
 */
case object RunMainTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    parameters.get("mainClassName") match {
      case Some(name) => {
        Log.info("running main in class " + name + " of project " + project)
        val mainArgs : List[String] = parameters.get("args").map(_.split(",").toList).getOrElse(Nil)
        val args = List(
          project.props.JavaHome() + "/bin/java",
          "-Dscala.usejavacp=true",
          "-classpath",
          project.runClasspath + ":" + project.scalaLibs.mkString(":"),
          "scala.tools.nsc.MainGenericRunner",
          parameters("mainClassName")) ::: mainArgs

        Command(args: _*).exec() match {
          case (0, _) => Right("Ok")
          case (code, msg) => Left(TaskFailed(ProjectAndTask(project, this), "Run Main failed in " + project + ", " + msg))
        }
      }
      case None =>
        Log.info("No class, nothing to do")
        Left(TaskFailed(ProjectAndTask(project, this), "No class specified"))
    }
  }
}
