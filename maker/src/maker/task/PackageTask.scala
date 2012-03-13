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
  def exec(project : Project, acc : List[AnyRef], parameters : Map[String, String] = Map()) = {
    import project._
    if (!packageDir.exists)
      packageDir.mkdirs

    val jar = project.props.JavaHome().getAbsolutePath + "/bin/jar"
    val dirsToPack = (project :: project.children).flatMap{p => 
      p.outputDir :: p.javaOutputDir :: p.resourceDirs // :: p.testOutputDir:
    }.filter(_.exists)

    dirsToPack.foreach(println)

    val cmds = if (parameters("webApp") != "true") {
      val createCmd = Command(List(jar, "cf", project.outputJar.getAbsolutePath, "-C", dirsToPack.head.getAbsolutePath, ".") : _*)
      val updateCmds = dirsToPack.tail.map(dir => List(jar, "uf", project.outputJar.getAbsolutePath, "-C", dir.getAbsolutePath, "."))
      createCmd :: updateCmds.map(args => Command(args : _*))
    }
    else {
      // basic structure
      // WEB-INF
      // WEB-INF/libs
      // WEB-INF/classes
      // META-INF

      val classDirsToPack = (project :: project.children).flatMap{p =>
        p.outputDir :: p.javaOutputDir :: Nil
      }.filter(_.exists)
      val resourcesToPack = (project :: project.children).flatMap(_.resourceDirs).filter(_.exists)

      val warName = project.outputJar.getAbsolutePath + project.outputJar.getName.replaceAll(".jar", ".war")
      val createCmd = Command(List(jar, "cf", project.outputJar.getAbsolutePath, "-C", project.root.getAbsolutePath + File.separator + "web.xml", ".") : _*)
      val classesCmds = classDirsToPack.map(dir => List(jar, "uf", warName, "-C", dir.getAbsolutePath, "WEB-INF/classes"))
      val resourcesCmds = resourcesToPack.map(dir => List(jar, "uf", warName, "-C", dir.getAbsolutePath, "WEB-INF"))
      val addLibs = Command(jar, "uf", warName, "-C", project.managedLibDir.getAbsolutePath, "WEB-INF/lib")

      createCmd :: classesCmds.map(args => Command(args : _*)) ::: resourcesCmds.map(args => Command(args : _*)) ::: List(addLibs)
    }

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
