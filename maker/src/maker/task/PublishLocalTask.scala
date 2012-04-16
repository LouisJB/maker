package maker.task

import maker.utils.FileUtils._
import org.apache.commons.io.FileUtils._
import maker.utils.maven._
import maker.project._
import maker.utils.{Version, Log}

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

    Log.info("PublishLocal for project " + project.name)
    Log.debug("parameters, " + parameters.mkString(", "))
    val confs = parameters.getOrElse("configurations", "default")
    val md = parameters.get("version") match {
      case Some(v) => moduleDef
        .copy(projectDef = moduleDef.projectDef
          .copy(moduleLibDef = moduleDef.projectDef.moduleLibDef
            .copy(gav = moduleDef.projectDef.moduleLibDef.gav.copy(version = Some(Version(v))))))
      case None => moduleDef
    }
    PomWriter.writePom(project.ivyFile, project.ivySettingsFile, pomFile, confs, md, project.props.PomTemplateFile())
    copyFileToDirectory(project.outputJar, moduleJarDir)

    Right("OK")
  }
}
