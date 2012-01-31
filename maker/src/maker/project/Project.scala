package maker.project 

import java.io.File
import java.lang.System
import tools.nsc.{Settings, Global}
import tools.nsc.reporters.ConsoleReporter
import plugin._
import scala.collection.immutable.MapProxy
import maker.utils.Log
import maker.task._
import maker.utils.FileUtils._
import maker.Props
import java.net.URLClassLoader
import maker.os.Command
import tools.nsc.io.{Directory, PlainDirectory}
import maker.graphviz.GraphVizUtils._
import maker.graphviz.GraphVizDiGrapher._


case class Project(
  name: String,
  root: File,
  sourceDirs: List[File] = Nil,
  tstDirs: List[File] = Nil,
  libDirs: List[File] = Nil,
  immediateDependentProjects: List[Project] = Nil,
  props : Props = Props()
) {

  def outputDir = file(root, "classes")
  def javaOutputDir = file(root, "java-classes")
  def testOutputDir = file(root, "test-classes")
  def packageDir = file(root, "package")
  def managedLibDir = file(root, "maker-lib")
  def ivySettingsFile = file("maker-ivysettings.xml")
  def ivyFile = file(root, "maker-ivy.xml")

  def srcDirs : List[File] = if (sourceDirs.isEmpty) List(file(root, "src")) else sourceDirs
  def testDirs : List[File] = if (tstDirs.isEmpty) List(file(root, "tests")) else tstDirs
  def jarDirs : List[File] = if (libDirs.isEmpty) List(file(root, "lib"), managedLibDir) else libDirs

  def dependsOn(projects: Project*) = copy(immediateDependentProjects = immediateDependentProjects ::: projects.toList)

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

  def urls = classpathDirectoriesAndJars.map(_.toURI.toURL).toArray
  def classpathDirectoriesAndJars : List[File] = ((outputDir :: javaOutputDir :: testOutputDir :: jars) ::: immediateDependentProjects.flatMap(_.classpathDirectoriesAndJars)).distinct
  def nonTestClasspathDirectoriesAndJars : List[File] = ((outputDir :: javaOutputDir :: jars) ::: immediateDependentProjects.flatMap(_.nonTestClasspathDirectoriesAndJars)).distinct
  def classLoader = {
    new URLClassLoader(urls)
  }

  def compilationClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")
  def runClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")
  def scalatestRunpath = (testOutputDir :: nonTestClasspathDirectoriesAndJars).mkString(" ")

  def printClasspath = compilationClasspath.split(":").foreach(println)

  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")

  private val makerDirectory = mkdirs(new File(root, ".maker"))

  def clean = QueueManager(allProjectDependencies + this, CleanTask)
  def compile = QueueManager(allProjectDependencies + this, CompileSourceTask)
  def javaCompile = QueueManager(allProjectDependencies + this, CompileJavaSourceTask)
  def testCompile = QueueManager(allProjectDependencies + this, CompileTestsTask)
  def test = {
    QueueManager(allProjectDependencies + this, RunUnitTestsTask)
    }
  def testOnly = QueueManager(Set(this), RunUnitTestsTask)
  def pack = QueueManager(allProjectDependencies + this, PackageTask)
  def update = QueueManager(allProjectDependencies + this, UpdateExternalDependencies)
  def updateOnly = QueueManager(Set(this), UpdateExternalDependencies)

  def ~ (task : () => BuildResult){
    var lastTaskTime : Option[Long] = None
    def printWaitingMessage = println("\nWaiting for source file changes (press 'enter' to interrupt)")
    def rerunTask{
      task()
      lastTaskTime = Some(System.currentTimeMillis)
      printWaitingMessage
    }

    printWaitingMessage
    while (true){
      Thread.sleep(1000)
      if (System.in.available  > 0 && System.in.read == 10)
        return;

      if (changedSrcFiles.size > 0){
        (lastTaskTime, lastModificationTime(srcFiles)) match {
          // Task has never been run
          case (None, _) => { rerunTask }

          // Code has changed since task last run
          case (Some(t1), Some(t2)) if t1 < t2 => { rerunTask }

          // Either no code yet or code has not changed
          case _ => 
        }
      }
    }
  }

  def allProjectDependencies : Set[Project] = {
    immediateDependentProjects.toSet ++ immediateDependentProjects.flatMap(_.allProjectDependencies)
  }

  def withinProjectTaskDependencies(task : Task) : Set[Task] = Task.standardWithinProjectDependencies.getOrElse(task, Set())
  def dependentProjectsTaskDependencies(task : Task) : Set[Task] = Task.standardDependentProjectDependencies.getOrElse(task, Set())

  def acrossProjectImmediateDependencies(task : Task) : Set[ProjectAndTask] = {
    val withinProjectDeps : Set[ProjectAndTask] = withinProjectTaskDependencies(task).map(ProjectAndTask(this, _)) 
    val childProjectDependencies : Set[ProjectAndTask] = immediateDependentProjects.flatMap{p => dependentProjectsTaskDependencies(task).map{t => ProjectAndTask(p, t)}}.toSet
    withinProjectDeps ++ childProjectDependencies
  }

  def dependentTasksWithinProject(task : Task) : Set[Task] = {
    withinProjectTaskDependencies(task).toSet ++ withinProjectTaskDependencies(task).flatMap(dependentTasksWithinProject(_)).toSet
  }

  def listDependentProjects(depth : Int = 100) : List[(Project, List[Project])] =
    if (depth >= 0)
      (this, immediateDependentProjects) :: immediateDependentProjects.flatMap(dp => dp.listDependentProjects(depth - 1))
    else
      Nil

  def showDependencyProjectGraph(depth : Int = 100, showLibDirs : Boolean = false, showLibs : Boolean = false) = showGraph(makeDot(listDependentProjects(depth), showLibDirs, showLibs))

  def listDependentLibs() : List[(Project, List[String])] =
    (this, Option(libDirs).map(_.flatMap(x => Option(x.listFiles()).map(_.toList.map(_.getPath)))).getOrElse(Nil).flatten) :: immediateDependentProjects.flatMap(dp => dp.listDependentLibs())

  def showDependencyLibraryGraph() = showGraph(makeDotFromString(listDependentLibs()))

  def delete = recursiveDelete(root)

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

  private val compiler_ : Global = makeCompiler(isTestCompiler = false)
  def compiler : Global = {
    compiler_.settings.classpath.value = compilationClasspath
    compiler_
  }
  private val testCompiler_ : Global = makeCompiler(isTestCompiler = true)
  def testCompiler : Global = {
    testCompiler_.settings.classpath.value = compilationClasspath
    testCompiler_
  }
}

class TopLevelProject(name:String,
                      immediateDependentProjects:List[Project],
                      props:Props = Props()) extends Project(name, file("."), Nil, Nil, Nil, immediateDependentProjects, props) {

  private def allProjects(project:Project):List[Project] = {
    if (project.immediateDependentProjects.isEmpty) {
      project :: Nil
    } else {
      project :: project.immediateDependentProjects.flatMap(allProjects(_))
    }.distinct
  }

  def generateIDEAProject() {
    val allModules = allProjects(this)

    IDEAProjectGenerator.generateTopLevelModule(root, name)
    IDEAProjectGenerator.generateIDEAProjectDir(root, name)
    allModules.filterNot(_ == this) foreach IDEAProjectGenerator.generateModule

    IDEAProjectGenerator.generateModulesFile(file(root, ".idea"), allModules.map(_.name))
  }
}

object Project {
  def apply(name : String) : Project = Project(name, file(name))
  def apply(name : String,  libDirectories : => List[File]) : Project = Project(name, file(name), libDirs = libDirectories)
}
