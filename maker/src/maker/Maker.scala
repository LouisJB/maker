package maker

import maker.project.Project
import maker.utils.FileUtils._
import java.io.File

object Maker {
  val MAKER_VERSION = ".1"

  val ivyFileName = "maker-ivy.xml"
  val ivySettingsFile_ = file("maker-ivysettings.xml")
  val props = Props(file("Maker.conf"))

  def mkProject(name : String, libs : List[File] = Nil) = {
    val root = file(name)
    new Project(
      name, 
      root,
      sourceDirs = file(root, "src") :: Nil,
      tstDirs = file(root, "tests") :: Nil,
      resourceDirs = file(root, "resources") :: Nil,
      libDirs=libs,
      props = props,
      ivySettingsFile = ivySettingsFile_,
      ivyFileRel = ivyFileName
    )
  }

  lazy val utils = mkProject("utils", List(file("utils/lib_managed"), file("libs/")))
  lazy val plugin = mkProject("plugin") dependsOn utils
  lazy val makerProj = mkProject("maker") dependsOn plugin

  lazy val mkr = makerProj

  var verboseTestOutput : Boolean = true
  var verboseTaskOutput : Boolean = true
}
