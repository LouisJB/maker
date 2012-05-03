package maker.task.tasks

import maker.project.Project
import maker.utils.FileUtils._
import maker.os.Command
import maker.utils.Log
import maker.utils.Utils._
import maker.task.{ProjectAndTask, TaskFailed, Task}
import org.apache.commons.io.FileUtils._

/**
 * Packages this project and its children
 * handles jars and wars, war is triggered by the presence of a web app directory (which is presumed contains the
 *   WEB-INF/web.xml and other essential webapp content)
 */
case object PackageTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    import project._
    if (!packageDir.exists)
      packageDir.mkdirs

    val jar = project.props.JavaHome().getAbsolutePath + "/bin/jar"
    val dirsToPack = {
      val dirs = (project :: project.children).flatMap {
        p =>
          p.outputDir :: p.javaOutputDir :: p.resourceDirs // :: p.testOutputDir:
      }.filter(_.exists)
      dirs.foreach(d => Log.debug(d.getAbsolutePath))
      dirs
    }

    def doPackage(pack : => List[Command]) : List[Command] = {
      if (fileIsLaterThan(project.outputArtifact, dirsToPack)) {
        Log.info("Packaging up to date for " + project.name + ", skipping...")
        Nil
      }
      else pack
    }

    val cmds = project.webAppDir match {
      case None => {
        doPackage {
          val createCmd = Command(List(jar, "cf", project.outputArtifact.getAbsolutePath, "-C", dirsToPack.head.getAbsolutePath, "."): _*)
          val updateCmds = dirsToPack.tail.map(dir => List(jar, "uf", project.outputArtifact.getAbsolutePath, "-C", dir.getAbsolutePath, "."))
          Log.info("Packaging artifact " + project.outputArtifact.getAbsolutePath)
          createCmd :: updateCmds.map(args => Command(args: _*))
        }
      }
      case Some(webAppDir) => {
        // basic structure
        // WEB-INF
        // WEB-INF/lib
        // WEB-INF/classes
        // META-INF/
        Log.info("Packaging web app, web app dir = " + webAppDir.getAbsolutePath)

        // build up the war structure image so we can make a web archive from it...
        val warImage = file(project.packagingRoot, "webapp")

        doPackage {
          Log.info("Making war image..." + warImage.getAbsolutePath)
          if (warImage.exists) recursiveDelete(warImage)
          warImage.mkdirs()

          // copy war content to image
          copyDirectory(webAppDir, warImage)
          dirsToPack.foreach(dir => copyDirectory(dir, file(warImage, "WEB-INF/classes")))

          // until we properly support scopes, treat lib provided as runtime provided scope and so exclude it from war packaging
          val allLibs = project.libDirs.filter(_.exists).flatMap(_.listFiles)
          Log.debug("allLibs: ")
          allLibs.foreach(f => Log.debug(f.getAbsolutePath))
          val providedLibJars : List[java.io.File] = project.providedLibDirs.filter(_.exists).flatMap(f => Option(f.listFiles).map(_.toList)).flatten
          Log.debug("classpath providedLibs libs:")
          providedLibJars.foreach(f => Log.debug(f.getAbsolutePath))
          Log.debug("additional named provided libs:")
          project.providedLibs.foreach(Log.debug(_))
          val allProvidedLibNames = project.providedLibs ::: providedLibJars.map(_.getName)
          val allLibsButNotProvidedLibs = allLibs.filter(f => !allProvidedLibNames.exists(libName => f.getName.contains(libName)))
          Log.debug("allLibsButNotProvidedLibs:")
          allLibsButNotProvidedLibs.foreach(f => {
            Log.debug(f.getAbsolutePath)
            copyFileToDirectory(f, file(warImage, "WEB-INF/lib"))
          })

          Log.info("Packaging artifact " + project.outputArtifact.getAbsolutePath)
          Command(List(jar, "cf", project.outputArtifact.getAbsolutePath, "-C", warImage.getAbsolutePath, "."): _*) :: Nil
        }
      }
    }

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
