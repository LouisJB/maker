package maker.task

import maker.project.Project
import java.io.File
import maker.os.{Command, Environment}


case class Package(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[Unit]{
  import Environment._
  import project._
  val lock = new Object
  def dependsOn(tasks : Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)


  def execSelf : Either[TaskFailed, Unit] = {
    if (!packageDir.exists)
      packageDir.mkdirs

    val cmd = Command(jar, "cf", outputJar.getAbsolutePath, "-C", new File(outputDir.getAbsolutePath).getParentFile.toString, outputDir.getName)
    cmd.exec match {
      case (0, _) => Right(Unit)
      case (errNo, errMessage) => Left(TaskFailed(this, errMessage))
    }
  }
}


