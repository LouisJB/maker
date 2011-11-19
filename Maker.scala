import maker.project.Project
import java.io.File
import org.apache.commons.io.{FileUtils => ApacheFileUtils}

System.setProperty("scala.usejavacp", "false")

def standardProject(name : String) = Project(
  name, 
  new File(name), 
  List(new File(name + "/src")), 
  List(new File("./lib")),
  Nil,
  Some("out/")
)

val utils = standardProject("utils")
val plugin = standardProject("plugin") dependsOn utils
val maker = new Project(
  "maker",
  new File("maker"),
  List(new File("maker/src")),
  List(new File("./lib")),
  Nil,
  Some("out/")
){
  def copyClasses{
    allDependencies().foreach{
      proj =>
        ApacheFileUtils.copyDirectory(proj.outputDir, new File("out"))
    }
  }
}

def copyPackage {
  ApacheFileUtils.copyFile(maker.outputJar, new File("maker.jar"))
}

