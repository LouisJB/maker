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
  def exec(implicit project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    project.webAppDir match {
      case Some(webAppDir) => {
        Log.info("running webapp of project " + project.name)

        val httpPort = parameters.getOrElse("portNo", "8080").toInt
        val warFile = project.outputArtifact
        val server = new Server(httpPort)

        val contextPath = "/" + project.name
        val webAppCtx = new WebAppContext(warFile.getAbsolutePath, contextPath)
        webAppCtx.setServer(server)

        // run a standard java-ee classloader strategy, maker env provides suitable container classpath for servlets etc
        webAppCtx.setParentLoaderPriority(false)
        server.setHandler(webAppCtx)

        Log.info("Starting HTTP on port: " + httpPort)
        server.start()

        //Log.info("Starting war context at " + contextPath + ", from " + warFile.getAbsolutePath)
        //webAppCtx.start()

        Log.info("Press ctrl-] to end...")

        def wait() {
          while (server.isRunning) {
            Thread.sleep(1000)
            if (System.in.available  > 0 && System.in.read == 29 /* ctrl-] */) {
              server.stop()
              return
            }
          }
        }
        wait()
        Right("OK")
      }
      case None => {
        Log.info("Project is not a web app, nothing to do...")
        Right("OK")
      }
    }
  }
}
