package maker.task.tasks

import maker.project.Project
import maker.utils.Log
import maker.task.Task
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

/**
 * run a simple web app using Jetty as a container
 */
case object RunJettyTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    project.webAppDir match {
      case Some(webAppDir) => {
        Log.info("running webapp of project " + project.name)

        val httpPort = parameters.get("portNo").map(_.toInt).getOrElse(8080)
        val warFile = project.outputArtifact
        val server = new Server(httpPort)

        Log.info("Starting HTTP on port: " + httpPort)
        server.start()

        val contextPath = "/" + warFile.getName.split('.').init.mkString(".")
        val webAppCtx = new WebAppContext(warFile.getAbsolutePath, contextPath)

        webAppCtx.setParentLoaderPriority(false)

        Log.info("Starting war context at " + contextPath + ", from " + warFile.getAbsolutePath)
        webAppCtx.start()

        Right("OK")
      }
      case None => {
        Log.info("Project is not a web app, nothing to do...")
        Right("OK")
      }
    }
  }
}
