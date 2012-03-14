package maker.task

import maker.project.Project
import maker.utils.FileUtils._
import maker.os.Command
import maker.utils.Log
import org.apache.commons.io._
import org.apache.commons.io.FileUtils._


/**
 * Packages this project and its children
 *   handles jars and wars, war is triggered by the presence of a web app directory (which is presumed contains the
 *   WEB-INF/web.xml and other essential webapp content)
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
    dirsToPack.foreach(d => Log.debug(d.getAbsolutePath))

    val cmds = project.webAppDir match {
      case None => {
        val createCmd = Command(List(jar, "cf", project.outputJar.getAbsolutePath, "-C", dirsToPack.head.getAbsolutePath, ".") : _*)
        val updateCmds = dirsToPack.tail.map(dir => List(jar, "uf", project.outputJar.getAbsolutePath, "-C", dir.getAbsolutePath, "."))
        createCmd :: updateCmds.map(args => Command(args : _*))
      }
      case Some(webAppDir) => {
        // basic structure
        // WEB-INF
        // WEB-INF/lib
        // WEB-INF/classes
        // META-INF/
        Log.info("packaging web app, web app dir = " + webAppDir.getAbsolutePath)
        val classDirsToPack = (project :: project.children).flatMap{p =>
          p.outputDir :: p.javaOutputDir :: Nil
        }.filter(_.exists)
        val resourceDirsToPack = (project :: project.children).flatMap(_.resourceDirs).filter(_.exists)

        // build up the war structure image so we can make a web archive from it...
        val warImage = file("package/webapp")
        Log.info("making war image..." + warImage.getAbsolutePath)
        if (warImage.exists) recursiveDelete(warImage)
        warImage.mkdirs()

        // copy war content to image
        copyDirectory(webAppDir, warImage)
        (classDirsToPack ::: resourceDirsToPack).foreach(dir => copyDirectory(dir, file(warImage, "WEB-INF/classes")))
        
        // until we properly support scopes, treat lib unmanaged as runtime provided scope 
        val allLibs = project.libDirs.filter(_.exists).flatMap(_.listFiles)
        Log.debug("allLibs: ")
        allLibs.foreach(f => Log.debug(f.getAbsolutePath))
        val unmanagedLibs = allLibs.filter(f => !project.managedLibDir.listFiles.toList.contains(f))
        Log.debug("unmanaged libs: ")
        unmanagedLibs.foreach(f => Log.debug(f.getAbsolutePath))
        val managedButNotUnmanagedLibs = allLibs.filter(f => !unmanagedLibs.exists(uf => uf.getName == f.getName))
        Log.debug("managedButNotUnmanaged: ")
        managedButNotUnmanagedLibs.foreach(f => {
            Log.debug(f.getAbsolutePath))
            copyFileToDirectory(f, file(warImage, "WEB-INF/lib")))
        }

        val warName = project.outputJar.getAbsolutePath.replaceAll(".jar", ".war") // quite weak, but just to get things working
        Log.info("packaging war " + warName)

        Command(List(jar, "cf", warName, "-C", warImage.getAbsolutePath, ".") : _*) :: Nil
      }
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
