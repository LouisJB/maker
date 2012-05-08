import maker.project.Project
import maker.Props
import java.io.File
import maker.utils.FileUtils._

val MAKER_VERSION = ".1"

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

val ivyFileName = "maker-ivy.xml"
val ivySettingsFile_ = file("maker-ivysettings.xml")
val propsFile = Props(file("Maker.conf"))

def mkProject(name : String, libs : List[File] = Nil) = {
  val root = file(name)
  new Project(
    name,
    root,
    sourceDirs = file(root, "src") :: Nil,
    tstDirs = file(root, "test") :: Nil,
    libDirs=libs,
    props = propsFile,
    ivySettingsFile = ivySettingsFile_,
    ivyFileRel = ivyFileName
  )
}

lazy val utils = mkProject("utils", List(file("utils/lib_managed"), file("libs/")))
lazy val plugin = mkProject("plugin") dependsOn utils
lazy val makerProj = mkProject("maker") dependsOn plugin

lazy val mkr = makerProj

println("\nMaker v" + MAKER_VERSION)

