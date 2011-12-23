import maker.project.Project
import maker._

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

lazy val libDirs = List(file("lib"))

lazy val utils = Project("utils", libDirectories = libDirs)
lazy val plugin = Project("plugin", libDirectories = libDirs) dependsOn utils
lazy val maker = Project("maker", libDirectories = libDirs) dependsOn plugin

