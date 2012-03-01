package maker.task

import maker.utils.FileUtils._
import org.apache.commons.io.{FileUtils => ApacheFileUtils}
import maker.utils.maven._
import maker.project._

case object PublishLocalTask extends Task{
  def exec(project : Project, acc : List[AnyRef]) = {
    val homeDir = project.props.HomeDir()
    val moduleDef = project.moduleDef
    val moduleLocal = file(homeDir, ".ivy2/maker-local/" + project.name)
    val moduleLocalPomDir = file(moduleLocal, "/poms/")
  
    moduleLocalPomDir.mkdirs    
    val pomFile = file(moduleLocalPomDir, "pom.xml")
    pomFile.createNewFile
    PomWriter.writePom(pomFile, moduleDef)

    val moduleJarDir = file(moduleLocal, "/jars/")
    moduleJarDir.mkdirs

    ApacheFileUtils.copyFileToDirectory(project.outputJar, moduleJarDir)
    
    Right("OK")
  }
}
