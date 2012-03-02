package maker.task

import maker.utils.FileUtils._
import org.apache.commons.io.FileUtils._
import maker.utils.maven._
import maker.project._
import maker.utils.Log

case object PublishLocalTask extends Task{
  def exec(project : Project, acc : List[AnyRef], parameters : Map[String, String] = Map()) = {
    val homeDir = project.props.HomeDir()
    val moduleDef = project.moduleDef
    val moduleLocal = file(homeDir, ".ivy2/maker-local/" + project.name)
    val moduleLocalPomDir = file(moduleLocal, "/poms/")
    moduleLocalPomDir.mkdirs
    val moduleJarDir = file(moduleLocal, "/jars/")
    moduleJarDir.mkdirs
    val pomFile = file(moduleLocalPomDir, "pom.xml")

    //pomFile.createNewFile
    //PomWriter.writePom(pomFile, moduleDef)

    Log.info("parameters, " + parameters.mkString(", "))
    val confs = parameters.getOrElse("configurations", "default")
    PomWriter.writePom(project.ivyFile, project.ivySettingsFile, pomFile, confs, moduleDef)
    copyFileToDirectory(project.outputJar, moduleJarDir)

    Right("OK")
  }
}
