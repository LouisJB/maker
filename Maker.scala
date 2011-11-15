import maker.project.Project
import java.io.File

System.setProperty("scala.usejavacp", "false")
def standardProject(name : String) = Project(
  name, 
  new File("./" + name), 
  List(new File(name + "/src")), 
  List(new File("./lib"))
)

val utils = standardProject("utils")
val plugin = standardProject("plugin") dependsOn utils
val maker = standardProject("maker") dependsOn plugin

import utils._
import scala.tools.nsc.{Settings, Global}
import scala.tools.util._
