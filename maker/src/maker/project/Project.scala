package maker.project 

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.lang.ProcessBuilder
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.System
import scala.collection.JavaConversions._
import tools.nsc.{Settings, Global}
import tools.nsc.io.{Directory, PlainDirectory}
import tools.nsc.reporters.ConsoleReporter
import plugin._
import scala.collection.immutable.MapProxy
import maker.utils.Log
import maker.utils.FileUtils
import maker.task._
import maker.utils.FileUtils._
import maker._

case class Project(
  name: String,
  root: File,
  sourceDirs: List[File] = Nil,
  tstDirs: List[File] = Nil,
  libDirs: List[File] = Nil,
  dependentProjects: List[Project] = Nil
) {
  import Project._


  def outputDir = file(root, "classes")
  def javaOutputDir = file(root, "java-classes")
  def testOutputDir = file(root, "test-classes")
  def packageDir = file(root, "package")
  def managedLibDir = file(root, "maker-lib")
  def ivySettingsFile = file("maker-ivysettings.xml")
  def ivyFile = file(root, "maker-ivy.xml")

  def srcDirs : List[File] = if (sourceDirs.isEmpty) List(file(root, "src")) else sourceDirs
  def testDirs : List[File] = if (tstDirs.isEmpty) List(file(root, "test")) else tstDirs
  def jarDirs : List[File] = if (libDirs.isEmpty) List(file(root, "lib"), managedLibDir) else libDirs

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
  def deletedSrcFiles = sourceToClassFiles.sourceFiles.filterNot(srcFiles()).filter{sf => srcDirs.exists(sf.isContainedIn(_))}
  def deletedTestFiles = sourceToClassFiles.sourceFiles.filterNot(testSrcFiles()).filter{sf => testDirs.exists(sf.isContainedIn(_))}

  def jars = findJars(jarDirs: _*).toList.sortWith(_.getPath < _.getPath)

  private def classpathDirectoriesAndJars : List[File] = ((outputDir :: javaOutputDir :: jars) ::: dependentProjects.flatMap(_.classpathDirectoriesAndJars)).distinct

  def compilationClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")
  def runClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")

  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")

  private val makerDirectory = mkdirs(new File(root, ".maker"))

  def clean = QueueManager(allDependencies(), CleanTask)
  def compile = QueueManager(allDependencies(), CompileSourceTask)
  def javaCompile = QueueManager(allDependencies(), CompileJavaSourceTask)
  def testCompile = QueueManager(allDependencies(), CompileTestsTask)
  def test = QueueManager(allDependencies(), RuntUnitTestsTask)
  def testOnly = QueueManager(Set(this), RuntUnitTestsTask)
  def pack = QueueManager(allDependencies(), PackageTask)
  def update = QueueManager(allDependencies(), UpdateExternalDependencies)
  def updateOnly = QueueManager(Set(this), UpdateExternalDependencies)

  val projectTaskDependencies = new MapProxy[Task, Set[Task]]{
    val self = Map[Task, Set[Task]](
      CompileSourceTask -> Set(CompileJavaSourceTask),
      CompileTestsTask -> Set(CompileSourceTask),
      RuntUnitTestsTask -> Set(CompileTestsTask)
    )
    override def default(task : Task) = Set[Task]()
  }

  def allTaskDependencies(task : Task) : Set[Task] = {
    def recurse(tasks : Set[Task], acc : Set[Task] = Set[Task]()) : Set[Task] = {
      if (tasks.forall(acc.contains))
        acc
      else
        recurse(
          (Set[Task]() /: tasks.map(projectTaskDependencies.getOrElse(_, Set[Task]())))(_ ++ _),
          acc ++ tasks
        )
    }
    recurse(Set(task))
  }
  def taskDependencies(task : Task) = allTaskDependencies(task).filterNot(_ == task)

  def delete = FileUtils.recursiveDelete(root)

  override def toString = "Project " + name

  val dependencies= plugin.Dependencies(new File(makerDirectory, "dependencies"))
  val signatures = plugin.ProjectSignatures(new File(makerDirectory, "signatures"))
  val sourceToClassFiles = plugin.SourceClassFileMapping(new File(makerDirectory, "sourceToClassFiles"))
  def classFilesFor(srcFile : File) = sourceToClassFiles.map.getOrElse(srcFile, Set[File]())

  def updateSignatures : Set[File] = {
    val changedFiles = signatures.filesWithChangedSigs
    signatures.persist
    changedFiles
  }

  private def makeCompiler(isTestCompiler : Boolean) = {
    val settings = new Settings
    val reporter = new ConsoleReporter(settings)
    val scalaAndJavaLibs = System.getProperty("sun.boot.class.path")

    settings.usejavacp.value = false
    val compilerOutputDir = if (isTestCompiler) testOutputDir else outputDir
    settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(compilerOutputDir)))
    settings.javabootclasspath.value = scalaAndJavaLibs
    settings.classpath.value = compilationClasspath

    new scala.tools.util.PathResolver(settings).result
    val comp = new Global(settings, reporter) {
      self =>
      override protected def computeInternalPhases() {
        super.computeInternalPhases
        phasesSet += new WriteDependencies(self, dependencies).Component
        phasesSet += new GenerateSigs(self, signatures).Component
        phasesSet += new DetermineClassFiles(self, compilerOutputDir, sourceToClassFiles).Component
      }
    }
    comp
  }

  val compiler: Global = makeCompiler(isTestCompiler = false)
  val testCompiler: Global = makeCompiler(isTestCompiler = true)
  def allDependencies(projectsSoFar : Set[Project] = Set()) : Set[Project] = {
    (Set(this) ++ dependentProjects.filterNot(projectsSoFar).flatMap(_.allDependencies(projectsSoFar + this)))
  }
}

