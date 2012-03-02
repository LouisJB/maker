package maker.task

import maker.project.Project
import maker.utils.Log
import maker.utils.FileUtils

case object CleanTask extends Task{
  def exec(project : Project, acc : List[AnyRef], parameters : Map[String, String] = Map()) = {
    Log.info("cleaning " + project)
    project.classFiles.foreach(_.delete)
    project.testClassFiles.foreach(_.delete)
    project.javaClassFiles.foreach(_.delete)
    project.outputJar.delete
    FileUtils.recursiveDelete(project.makerDirectory)
    Right(Unit)
  }
}

