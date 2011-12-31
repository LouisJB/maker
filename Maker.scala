import maker.project.Project
import maker.utils.FileUtils._
import maker.Props

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

val props = Props(file("Maker.conf"))
lazy val libDirs = List(file("lib"))

lazy val utils = Project("utils", file("utils"), libDirs = libDirs, props = props)
lazy val plugin = Project("plugin", file("plugin"), libDirs = libDirs, props = props) dependsOn utils
lazy val maker = Project("maker", file("maker"), libDirs = libDirs, props = props) dependsOn plugin

