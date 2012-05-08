package maker

import maker.project.Project
import maker.utils.FileUtils._
import maker.utils.Log._
import java.io.File

object Maker{
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

}
