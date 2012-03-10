import maker.project.Project
import maker.utils.FileUtils._
import maker.Props
import java.io.File

val MAKER_VERSION = ".1"

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

val ivyFileName = "ivy.xml"
val ivySettingsFile_ = file("ivysettings.xml")
val propsFile = Props(file("Maker.conf"))
lazy val libDirs = List(file(".maker/lib"), file("libs"))

def mkProject(name : String, libs : List[File]) = new Project(
  name, file(name), libs, props = propsFile,
  ivySettingsFile = ivySettingsFile_,
  ivyFileRel = ivyFileName
)

lazy val utils = mkProject("utils", file("utils/maker-lib") :: libDirs)
lazy val plugin = mkProject("plugin", libDirs) dependsOn utils
lazy val makerProj = mkProject("maker", libDirs) dependsOn plugin

lazy val mkr = makerProj

println("\nMaker v" + MAKER_VERSION)

