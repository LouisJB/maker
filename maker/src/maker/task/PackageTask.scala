package maker.task

import maker.project.Project
import maker.utils.FileUtils
import java.io.File
import org.apache.commons.io.{FileUtils => ApacheFileUtils}
import maker.os.Command

/**
 * Packages this project and its children - can't think of a good
 * reason to have a packageOnly task
 */
case object PackageTask extends Task{

  def exec(project : Project, acc : List[AnyRef]) = {
    import project._
    if (!packageDir.exists)
      packageDir.mkdirs
    //FileUtils.withTempDir({
        //dir : File =>
        //project.projectAndDescendents.foreach{
          //depProj =>
          //ApacheFileUtils.copyDirectory(depProj.outputDir, dir)
          //}
    val jar = project.props.JavaHome().getAbsolutePath + "/bin/jar"
    val dirsToPack = (project :: project.children).flatMap{p => 
      p.outputDir::p.javaOutputDir::p.testOutputDir::p.resourceDirs
    }.filter(_.exists)
    dirsToPack.foreach(println)
    val cmds = List(jar, "cf", project.outputJar.getAbsolutePath) ::: dirsToPack.flatMap{dir => List("-C", dir.getAbsolutePath, ".")}
    println(cmds.mkString(" "))
    val cmd = Command(cmds : _*)
    cmd.exec() match {
      case (0, _) => Right(Unit)
      case (errNo, errMessage) => Left(TaskFailed(ProjectAndTask(project, this), errMessage))
    }
  }
}
