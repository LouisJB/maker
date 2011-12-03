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
import maker.utils.FileUtils


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
  testDirs: List[File],
  jarDirs: List[File],
  dependentProjects: List[Project] = Nil,
  compilationClasspathOverride : Option[String] = None
) {

  def outputDir = new File(root, "classes")
  def javaOutputDir = new File(root, "java-classes")
  def testOutputDir = new File(root, "test-classes")
  def packageDir = new File(root, "package")
  import Project._
  import maker.utils.FileUtils._

  def dependsOn(projects: Project*) = copy(dependentProjects = dependentProjects ::: projects.toList)

  def srcFiles() = findSourceFiles(srcDirs: _*)
  def testSrcFiles() = findSourceFiles(testDirs: _*)
  def javaSrcFiles() = findJavaSourceFiles(srcDirs: _*)

  def classFiles = findClasses(outputDir) 
  def javaClassFiles = findClasses(javaOutputDir) 
  def testClassFiles = findClasses(testOutputDir)

  private def lastModificationTime(files : Set[File]) = files.toList.map(_.lastModified).sortWith(_ > _).headOption
  def compilationTime: Option[Long] = lastModificationTime(classFiles)
  def javaCompilationTime: Option[Long] = lastModificationTime(javaClassFiles)
  def testCompilationTime: Option[Long] = lastModificationTime(testClassFiles)

  private def filterChangedSrcFiles(files : Set[File], modTime : Option[Long]) = {
    modTime match {
      case Some(time) => files.filter(_.lastModified > time)
      case None => files
    }
  }
  def changedSrcFiles = filterChangedSrcFiles(srcFiles(), compilationTime)
  def changedJavaFiles = filterChangedSrcFiles(javaSrcFiles(), javaCompilationTime)
  def changedTestFiles = filterChangedSrcFiles(testSrcFiles(), testCompilationTime)

  def jars = findJars(jarDirs: _*).toList.sortWith(_.getPath < _.getPath)

  private def classpathDirectoriesAndJars : List[File] = ((outputDir :: javaOutputDir :: jars) ::: dependentProjects.flatMap(_.classpathDirectoriesAndJars)).distinct

  def compilationClasspath = (compilationClasspathOverride.toList ::: classpathDirectoriesAndJars.map(_.getAbsolutePath)).mkString(":")
  def runClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")

  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")

  private val cleanTask : Clean = Clean(this, dependentProjects.map(_.cleanTask))
  private val javaCompileTask : JavaCompile = JavaCompile(this, dependentProjects.map(_.compileTask))
  private val compileTask : Compile = Compile(this, dependentProjects.map(_.compileTask)) dependsOn javaCompileTask
  private val testCompileTask : TestCompile = TestCompile(this, compileTask::dependentProjects.map(_.testCompileTask))
  private val packageTask = Package(this) dependsOn (compileTask)
  private val makerDirectory = mkdirs(new File(root, ".maker"))

  private val testTask : Test = Test(this, testCompileTask :: dependentProjects.map(_.testTask))
  private val testOnlyTask : Test = Test(this, List(testCompileTask))

  def clean = cleanTask.exec

  def compile = compileTask.exec
  def javaCompile = javaCompileTask.exec
  def testCompile = testCompileTask.exec
  def test = testTask.exec
  def testOnly = testOnlyTask.exec

  def pack = packageTask.exec

  def delete = FileUtils.recursiveDelete(root)

  override def toString = "Project " + name

  val dependencies= plugin.Dependencies(new File(makerDirectory, "dependencies"))
  var signatures = plugin.ProjectSignatures()
  private val signatureFile = new File(makerDirectory, "signatures")
  def updateSignatures : Set[File] = {
    val olderSigs = ProjectSignatures(signatureFile)
    val changedFiles = signatures.changedFiles(olderSigs)
    signatures.persist(signatureFile)
    Log.debug("Files with changed sigs " + changedFiles.mkString("\n\t", "\n\t", ""))
    Log.debug("Sig changes\n" + signatures.changeAsString(olderSigs))
    changedFiles
  }

  private def makeCompiler(isTestCompiler : Boolean) = {
    val settings = new Settings
    val reporter = new ConsoleReporter(settings)
    val scalaAndJavaLibs = System.getProperty("sun.boot.class.path")

    settings.usejavacp.value = false
    settings.outputDirs.setSingleOutput(
      new PlainDirectory(new Directory(
        if (isTestCompiler) testOutputDir else outputDir
    )))
    settings.javabootclasspath.value = scalaAndJavaLibs
    settings.classpath.value = compilationClasspath

    new scala.tools.util.PathResolver(settings).result
    val comp = new Global(settings, reporter) {
      self =>
      override protected def computeInternalPhases() {
        super.computeInternalPhases
        phasesSet += new WriteDependencies(self, dependencies).Component
        phasesSet += new GenerateSigs(self, signatures).Component
      }

    }
    comp
  }

  val compiler: Global = makeCompiler(isTestCompiler = false)
  val testCompiler: Global = makeCompiler(isTestCompiler = true)
  def allDependencies(projectsSoFar : Set[Project] = Set()) : List[Project] = {
    (this :: dependentProjects.filterNot(projectsSoFar).flatMap(_.allDependencies(projectsSoFar + this))).distinct
  }

}

