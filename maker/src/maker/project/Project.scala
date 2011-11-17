package maker.project

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
import plugin._
import collection.Set


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
  dependentProjects: List[Project] = Nil,
  classpathOverride : Option[String] = None
) {

  def outputDir = new File(root, "out")
  def packageDir = new File(root, "package")
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

  private def classpathFiles : List[File] = ((outputDir :: jars) ::: dependentProjects.flatMap(_.classpathFiles)).distinct

  def classpath = (classpathOverride.toList ::: classpathFiles.map(_.getAbsolutePath)).mkString(":")

  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")

  private val cleanTask = Clean(this)
  private val compileTask = Compile(this)
  private val packageTask = Package(this) dependsOn (compileTask)
  private val makerDirectory = mkdirs(new File(root, ".maker"))


  def clean = cleanTask.exec

  def compile = {println("Compiling"); compileTask.exec}

  def pack = packageTask.exec

  def delete = recursiveDelete(root)

  override def toString = "Project " + name

  def compileRequired = {
    changedSrcFiles.size > 0
  }

  val dependencies= plugin.Dependencies(new File(makerDirectory, "dependencies"))
  private var signatures = plugin.ProjectSignatures()
  private val signatureFile = new File(makerDirectory, "signatures")
  def updateSignatures : Set[File] = {
    val olderSigs = ProjectSignatures(signatureFile)
    val changedFiles = signatures.changedFiles(olderSigs)
    signatures.persist(signatureFile)
    changedFiles
  }

  val settings = new Settings
  val reporter = new ConsoleReporter(settings)
  val compiler: Global = {
    val scalaAndJavaLibs = System.getProperty("sun.boot.class.path")

    settings.usejavacp.value = false
    settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(outputDir)))
    settings.javabootclasspath.value = scalaAndJavaLibs
    settings.classpath.value = classpath

    println(this)
    println("classpath = " + classpath)
    new scala.tools.util.PathResolver(settings).result
    val comp = new Global(settings, reporter) {
      self =>
//      phasesSet ++= new BrowsePlugin(self).components
      override protected def computeInternalPhases() {
        super.computeInternalPhases
//        phasesSet += new WriteDependencies(self, dependencies).Component
//        phasesSet += new GenerateSigs(self, signatures).Component
//        phasesSet ++= new BrowsePlugin(self).components
      }

//      override protected lazy val roughPluginsList = List(new GenerateSigs(self))
//      lazy val pubRoughPlugins = roughPluginsList
    }
//    comp.pubRoughPlugins.foreach(println)
//    println("platform Classpath = " + comp.platform.classPath)
    comp
  }

  def allDependencies(projectsSoFar : Set[Project] = Set()) : List[Project] = {
    (this :: dependentProjects.filterNot(projectsSoFar).flatMap(_.allDependencies(projectsSoFar + this))).distinct
  }

}

