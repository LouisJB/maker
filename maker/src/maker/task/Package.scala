package maker.task

import maker.project.Project
import java.io.File
import maker.os.{Command, Environment}
import org.apache.commons.io.{FileUtils => ApacheFileUtils}
import maker.utils.FileUtils


//case class Package(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[Unit]{
//  import Environment._
//  import project._
//  val lock = new Object
//  def dependsOn(tasks : Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)
//
//
//  def execSelf : Either[TaskFailed, Unit] = {
//    if (!packageDir.exists)
//      packageDir.mkdirs
//    FileUtils.withTempDir({
//      dir : File =>
//        project.allDependencies().foreach{
//          depProj =>
//            ApacheFileUtils.copyDirectory(depProj.outputDir, dir)
//        }
//      val cmd = Command(jar, "cf", project.outputJar.getAbsolutePath, "-C", dir.getAbsolutePath, ".")
//      cmd.exec match {
//        case (0, _) => Right(Unit)
//        case (errNo, errMessage) => Left(TaskFailed(this, errMessage))
//      }
//    }, false)
//
//  }
//}


