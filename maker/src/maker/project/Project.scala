package maker.project 

import java.io.File
import java.lang.System
import maker.utils.Log
import maker.task._
import maker.utils.FileUtils._
import maker.Props
import java.net.URLClassLoader
import maker.os.Command
import maker.graphviz.GraphVizUtils._
import maker.graphviz.GraphVizDiGrapher._

case class Project(
  name: String,
  root: File,
  sourceDirs: List[File] = Nil,
  tstDirs: List[File] = Nil,
  libDirs: List[File] = Nil,
  resourceDirs : List[File] = Nil,
  children: List[Project] = Nil,
  props : Props = Props()
) {

  def outputDir = file(root, "classes")
  def javaOutputDir = file(root, "java-classes")
  def testOutputDir = file(root, "test-classes")
  def packageDir = file(root, "package")
  def managedLibDir = file(root, "maker-lib")
  def ivySettingsFile = file("maker-ivysettings.xml")
  def ivyFile = file(root, "maker-ivy.xml")
  val makerDirectory = mkdirs(new File(root, ".maker"))

  def srcDirs : List[File] = if (sourceDirs.isEmpty) List(file(root, "src")) else sourceDirs
  def testDirs : List[File] = if (tstDirs.isEmpty) List(file(root, "tests")) else tstDirs
  def jarDirs : List[File] = if (libDirs.isEmpty) List(file(root, "lib"), managedLibDir) else libDirs

  def dependsOn(projects: Project*) = copy(children = children ::: projects.toList)

  def srcFiles() = findSourceFiles(srcDirs: _*)
  def testSrcFiles() = findSourceFiles(testDirs: _*)
  def javaSrcFiles() = findJavaSourceFiles(srcDirs: _*)

  def classFiles = findClasses(outputDir) 
  def javaClassFiles = findClasses(javaOutputDir) 
  def testClassFiles = findClasses(testOutputDir)


  def jars = findJars(jarDirs: _*).toList.sortWith(_.getPath < _.getPath)

  def classpathDirectoriesAndJars : List[File] = ((outputDir :: javaOutputDir :: testOutputDir :: jars) ::: resourceDirs ::: children.flatMap(_.classpathDirectoriesAndJars)).distinct
  def compilationClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")
  def runClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")
  def scalatestRunpath = classpathDirectoriesAndJars.mkString(" ")
  def printClasspath = compilationClasspath.split(":").foreach(println)

  def classLoader = {
    val urls = classpathDirectoriesAndJars.map(_.toURI.toURL).toArray
    new URLClassLoader(urls)
  }

  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")


  val state = ProjectState(this)
  val compilers = ProjectCompilers(this)
  val dependencies = ProjectDependencies(this)

  /**********************
    Tasks
  **********************/

  def projectAndDescendents = this::dependencies.descendents.toList
  def clean = QueueManager(projectAndDescendents, CleanTask)
  def compile = QueueManager(projectAndDescendents, CompileSourceTask)
  def javaCompile = QueueManager(projectAndDescendents, CompileJavaSourceTask)
  def testCompile = QueueManager(projectAndDescendents, CompileTestsTask)
  def test = QueueManager(projectAndDescendents, RunUnitTestsTask)
  def testOnly = QueueManager(List(this), RunUnitTestsTask)
  def pack = QueueManager(projectAndDescendents, PackageTask)
  def update = QueueManager(projectAndDescendents, UpdateExternalDependencies)
  def updateOnly = QueueManager(List(this), UpdateExternalDependencies)

  def ~ (task : () => BuildResult){
    var lastTaskTime : Option[Long] = None
    def printWaitingMessage = println("\nWaiting for source file changes (press 'enter' to interrupt)")
    def rerunTask{
      task()
      lastTaskTime = Some(System.currentTimeMillis)
      printWaitingMessage
    }

    def anySourceFileHasChanged = {
      projectAndDescendents.exists{
        proj => 
          val changed = proj.state.changedSrcFiles ++ proj.state.changedTestFiles ++ proj.state.changedJavaFiles
          changed.size > 0
      }
    }
    def lastSrcModificationTime = {
      projectAndDescendents.map{proj => proj.state.lastModificationTime(proj.srcFiles ++ proj.testSrcFiles ++ proj.javaSrcFiles)}.max
    }
    printWaitingMessage
    while (true){
      Thread.sleep(1000)
      if (System.in.available  > 0 && System.in.read == 10)
        return;

      if (anySourceFileHasChanged){
        (lastTaskTime,  lastSrcModificationTime) match {
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

  def showDependencyProjectGraph(depth : Int = 100, showLibDirs : Boolean = false, showLibs : Boolean = false) = {
    def dependentProjects(project : Project, depth : Int) : List[(Project, List[Project])] = {
      (project, project.children) :: project.children.flatMap(dependentProjects(_, depth - 1))
    }
    showGraph(makeDot(dependentProjects(this, depth), showLibDirs, showLibs))
  }

  def showDependencyLibraryGraph() = {
    val dependentLibs = (projectAndDescendents.toList).map{
      proj =>
        proj → proj.jars.map(_.getPath)
    }
    
    showGraph(makeDotFromString(dependentLibs))
  }



  def delete = recursiveDelete(root)

  override def toString = "Project " + name


}

class TopLevelProject(name:String,
                      children:List[Project],
                      props:Props = Props()) extends Project(name, file("."), Nil, Nil, Nil, Nil, children, props) {

  def generateIDEAProject() {
    val allModules = children.flatMap(_.projectAndDescendents).distinct

    IDEAProjectGenerator.generateTopLevelModule(root, name)
    IDEAProjectGenerator.generateIDEAProjectDir(root, name)
    allModules foreach IDEAProjectGenerator.generateModule

    IDEAProjectGenerator.generateModulesFile(file(root, ".idea"), (this :: allModules).map(_.name))
  }
}

object Project {
  def apply(name : String) : Project = Project(name, file(name))
  def apply(name : String,  libDirectories : => List[File]) : Project = Project(name, file(name), libDirs = libDirectories, props = Props())
  def apply(name : String,  libDirectories : => List[File], props : Props) : Project = Project(name, file(name), libDirs = libDirectories, props = props)
}
