package maker.task.tasks

import maker.project.Project
import maker.utils.Log
import maker.utils.FileUtils._
import maker.task.Task
import maker.Maker


/** Clean task - cleans up all build artifacts from the classpath
  *
  *  removes all build content and directories that contained it
  */
case object CleanTask extends Task {
  def exec(implicit project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    if (Maker.verboseTaskOutput)
      Log.debug("cleaning " + project)
    else
      print(".")

    // thoroughly clean up everything as we don't want lingering files or even empty dirs messing up a subsequent builds
    recursiveDelete(project.outputDir)
    recursiveDelete(project.testOutputDir)
    recursiveDelete(project.javaOutputDir)
    recursiveDelete(project.packagingRoot)
    recursiveDelete(project.makerDirectory)
    recursiveDelete(project.docRoot)

    project.outputArtifact.delete

    Right(Unit)
  }
}
