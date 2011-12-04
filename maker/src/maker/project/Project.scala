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
import scala.collection.immutable.MapProxy


//object Project {
//
//  private def java_home = ("/usr/local/jdk" :: List("JAVA_HOME", "MAKER_JAVA_HOME").flatMap {
//    e: String => Option(System.getenv(e))
//  }).filter(new File(_).exists).headOption.getOrElse(throw new Exception("Can't find scala home"))
//
//  private def jar: String = java_home + "/bin/jar"
//
//}

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

  private val makerDirectory = mkdirs(new File(root, ".maker"))

  def clean = QueueManager(allDependencies(), CleanTask)
  def compile = QueueManager(allDependencies(), CompileSourceTask)
  def javaCompile = QueueManager(allDependencies(), CompileJavaSourceTask)
  def testCompile = QueueManager(allDependencies(), CompileTestsTask)
  def test = QueueManager(allDependencies(), RuntUnitTestsTask)
  def testOnly = QueueManager(Set(this), RuntUnitTestsTask)
  def pack = QueueManager(allDependencies(), PackageTask)

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

    //def allProjectTaskDependencies(task : SingleProjectTask) : Set[ProjectAndTask] = allTaskDependencies(task).map(ProjectAndTask(this, _)) ++ dependentProjects.flatMap(_.allProjectTaskDependencies(task))
  //def projectTaskDependencies(task : SingleProjectTask) = allProjectTaskDependencies(task).filterNot(_ == ProjectAndTask(this, task))

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
  def allDependencies(projectsSoFar : Set[Project] = Set()) : Set[Project] = {
    (Set(this) ++ dependentProjects.filterNot(projectsSoFar).flatMap(_.allDependencies(projectsSoFar + this)))
  }
}

