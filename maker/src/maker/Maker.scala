package maker

import maker.project.Project
import maker.utils.FileUtils._
import maker.utils.Log._
import java.io.File

object Maker{
  val ivyFileName = "ivy.xml"
  val ivySettingsFile_ = file("ivysettings.xml")
  val props = Props(file("Maker.conf"))

  def mkProject(name : String, libs : List[File] = Nil) = new Project(
    name, file(name),
    libDirs=libs,
    resourceDirs = List(file(name + "/resources")),
    props = props,
    ivySettingsFile = ivySettingsFile_,
    ivyFileRel = ivyFileName
  )

  lazy val utils = mkProject("utils", List(file("utils/maker-lib"), file("libs/")))
  lazy val plugin = mkProject("plugin") dependsOn utils
  lazy val makerProj = mkProject("maker") dependsOn plugin

  lazy val mkr = makerProj
}
