package maker.task

import maker.project.Project
import maker.utils.FileUtils
import java.io.File
import org.apache.commons.io.{FileUtils => ApacheFileUtils}
import maker.os.Command

case object PackageTask extends Task{
  def exec(project : Project, acc : List[AnyRef]) = {
    import project._
    if (!packageDir.exists)
      packageDir.mkdirs
    FileUtils.withTempDir({
      dir : File =>
        project.projectAndDescendents.foreach{
          depProj =>
            ApacheFileUtils.copyDirectory(depProj.outputDir, dir)
        }
      val jar = project.props.JavaHome().getAbsolutePath + "/bin/jar"
      val cmd = Command(jar, "cf", project.outputJar.getAbsolutePath, "-C", dir.getAbsolutePath, ".")
      cmd.exec() match {
        case (0, _) => Right(Unit)
        case (errNo, errMessage) => Left(TaskFailed(ProjectAndTask(project, this), errMessage))
      }
    }, false)
  }
}
