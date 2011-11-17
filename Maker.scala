import maker.project.Project
import java.io.File
import org.apache.commons.io.FileUtils

System.setProperty("scala.usejavacp", "false")

def standardProject(name : String) = Project(
  name, 
  new File("./" + name), 
  List(new File(name + "/src")), 
  List(new File("./lib"), new File("./package")),
  Nil,
  Some("maker.jar")
)

val utils = standardProject("utils")
val plugin = standardProject("plugin") dependsOn utils
val maker = standardProject("maker") dependsOn plugin

def copyPackage {
  FileUtils.copyFile(maker.outputJar, new File("maker.jar"))
}

