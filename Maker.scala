import maker.project.Project
import maker.utils.FileUtils._
import maker.Props
import maker.utils.Log._
import java.io.File

val MAKER_VERSION = ".1"

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

val ivyFileName = "ivy.xml"
val ivySettingsFile_ = file("ivysettings.xml")
val propsFile = Props(file("Maker.conf"))

def mkProject(name : String, libs : List[File] = Nil) = new Project(
  name, file(name),
  libDirs=libs,
  props = propsFile,
  ivySettingsFile = ivySettingsFile_,
  ivyFileRel = ivyFileName
)

lazy val utils = mkProject("utils", List(file("utils/maker-lib"), file("libs/")))
lazy val plugin = mkProject("plugin") dependsOn utils
lazy val makerProj = mkProject("maker") dependsOn plugin

lazy val mkr = makerProj

println("\nMaker v" + MAKER_VERSION)

