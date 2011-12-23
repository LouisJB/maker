import maker.project.Project

System.setProperty("scala.usejavacp", "false")

lazy val utils = Project("utils")
lazy val plugin = Project("plugin") dependsOn utils
lazy val maker = Project("maker") dependsOn plugin

