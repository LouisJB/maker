import maker.project.Project
import java.io.File
import org.apache.commons.io.{FileUtils => ApacheFileUtils}

System.setProperty("scala.usejavacp", "false")

def standardProject(name : String) = Project(
  name, 
  new File(name), 
  List(new File(name + "/src")), 
  List(new File(name + "/tests")), 
  List(new File("./lib")),
  Nil
)

lazy val utils = standardProject("utils")
lazy val plugin = standardProject("plugin") dependsOn utils
lazy val maker = standardProject("maker") dependsOn plugin

