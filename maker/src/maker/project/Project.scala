package maker.project

import plugin.WriteDependencies
import maker.utils.Log
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.lang.ProcessBuilder
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.System
import scala.collection.JavaConversions._
import maker.task._
import tools.nsc.{Settings, Global}
import tools.nsc.io.{Directory, PlainDirectory}
import tools.nsc.reporters.ConsoleReporter

object Project {

  private def java_home = ("/usr/local/jdk" :: List("JAVA_HOME", "MAKER_JAVA_HOME").flatMap {
    e: String => Option(System.getenv(e))
  }).filter(new File(_).exists).headOption.getOrElse(throw new Exception("Can't find scala home"))

  private def jar: String = java_home + "/bin/jar"

}

case class Project(
                    name: String,
                    root: File,
                    srcDirs: List[File],
                    jarDirs: List[File],
                    outputDir: File,
                    packageDir: File,
                    dependentProjects: List[Project] = Nil
                    ) {

  import Project._
  import maker.utils.FileUtils._

  def dependsOn(projects: Project*) = copy(dependentProjects = dependentProjects ::: projects.toList)

  def srcFiles() = findSourceFiles(srcDirs: _*)

  def classFiles = findClasses(outputDir)

  def compilationTime(): Option[Long] = classFiles.toList.map(_.lastModified).sortWith(_ > _).headOption

  def changedSrcFiles = compilationTime match {
    case Some(time) => srcFiles().filter(_.lastModified > time)
    case None => srcFiles()
  }

  def jars = findJars(jarDirs: _*).toList.sortWith(_.getPath < _.getPath)

  def classpath = (outputDir :: jars).map(_.getAbsolutePath).mkString(":")

  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")

  private val cleanTask = Clean(this)
  private val compileTask = Compile(this)
  private val packageTask = Package(this) dependsOn (compileTask)
  private val makerDirectory = mkdirs(new File(root, ".maker"))

  def clean = cleanTask.exec

  def compile = compileTask.exec

  def pack = packageTask.exec

  def delete = recursiveDelete(root)

  override def toString = "Project " + name

  def compileRequired = {
    changedSrcFiles.size > 0
  }

  val dependencies = plugin.Dependencies(new File(makerDirectory, "dependencies"))

  val signatures = plugin.SourceFileSignatures(new File(makerDirectory, "signatures"))
  val compiler: Global = {
    val settings = new Settings
    settings.usejavacp.value = true
    settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(outputDir)))

    new Global(settings, new ConsoleReporter(settings)) {
      self =>
      override protected def computeInternalPhases() {
        super.computeInternalPhases
        phasesSet += new WriteDependencies(self, dependencies).Component
      }
    }
  }


}

