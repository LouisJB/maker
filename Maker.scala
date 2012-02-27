import maker.project.Project
import maker.utils.FileUtils._
import maker.Props

val MAKER_VERSION = ".1"

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

val props = Props(file("Maker.conf"))
lazy val libDirs = List(file(".maker/lib"), file("libs"))

lazy val utils = new Project("utils", file("utils"), libDirs = file("utils/maker-lib")::libDirs, props = props){
  override def ivyFile=file("ivy.xml")
  override def ivySettingsFile=file("ivysettings.xml")
}
lazy val plugin = Project("plugin", file("plugin"), libDirs = libDirs, props = props) dependsOn utils
lazy val makerProj = Project("maker", file("maker"), libDirs = libDirs, props = props) dependsOn plugin
lazy val mkr = makerProj

println("\nMaker v" + MAKER_VERSION)
