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
      p.outputDir :: p.javaOutputDir :: p.resourceDirs // :: p.testOutputDir:
    }.filter(_.exists)

    dirsToPack.foreach(println)
    val createCmd = Command(List(jar, "cf", project.outputJar.getAbsolutePath, "-C", dirsToPack.head.getAbsolutePath, ".") : _*)
    val updateCmds = dirsToPack.tail.map(dir => List(jar, "uf", project.outputJar.getAbsolutePath, "-C", dir.getAbsolutePath, "."))

    val cmds = createCmd :: updateCmds.map(args => Command(args : _*))

    def fix[A,B](f: (A=>B)=>(A=>B)): A=>B = f(fix(f))(_)

    fix[List[Command], Either[TaskFailed, AnyRef]](exec => cs => {
      cs match {
        case cmd :: rs => cmd.exec() match {
          case (0, _) => exec(rs)
          case (errNo, errMessage) => Left(TaskFailed(ProjectAndTask(project, this), errMessage))
        }
        case Nil => Right(Unit)
      }
    })(cmds)
  }
}
