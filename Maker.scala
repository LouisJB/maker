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
  Nil,
  Some("out/")
)

val utils = standardProject("utils")
val plugin = standardProject("plugin") dependsOn utils
val maker = standardProject("maker") dependsOn plugin

//def copyClasses{
  //maker.compile match {
    //case Left(_) => 
    //case Right(_) => 
    //maker.allDependencies().foreach{
      //proj =>
      //ApacheFileUtils.copyDirectory(proj.outputDir, new File("out"))
      //}
      //}
      //}
      //
      //def copyPackage {
        //ApacheFileUtils.copyFile(maker.outputJar, new File("maker.jar"))
        //}

