package maker.task.tasks

import maker.project.Project
import maker.utils.Log
import maker.utils.FileUtils._
import maker.task.Task
import maker.Maker

case object CleanTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    if (Maker.verboseTaskOutput)
      Log.debug("cleaning " + project)
    else
      print(".")
    project.classFiles.foreach(_.delete)
    project.testClassFiles.foreach(_.delete)
    project.javaClassFiles.foreach(_.delete)
    project.outputArtifact.delete
    recursiveDelete(project.packagingRoot)
    recursiveDelete(project.makerDirectory)
    Right(Unit)
  }
}

