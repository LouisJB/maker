package maker.project

import java.io.File
import java.lang.System
import maker.task._
import maker.utils.FileUtils._
import maker.Props
import java.net.URLClassLoader
import maker.os.Command
import maker.graphviz.GraphVizUtils._
import maker.graphviz.GraphVizDiGrapher._
import maker.utils.{DependencyLib, Log}
import maker.utils.RichString._
import maker.utils.FileUtils

case class Project(
  name: String,
  root: File,
  sourceDirs: List[File] = Nil,
  tstDirs: List[File] = Nil,
  libDirs: List[File] = Nil,
  resourceDirs : List[File] = Nil,
  children: List[Project] = Nil,
  props : Props = Props(),
  description : Option[String] = None,
  ivySettingsFile : File = file("maker-ivysettings.xml"), // assumption this is typically absolute and module ivy is relative, this might be invalid?
  ivyFileRel : String = "maker-ivy.xml"
) {

  def outputDir = file(root, "classes")
  def javaOutputDir = file(root, "java-classes")
  def testOutputDir = file(root, "test-classes")
  def packageDir = file(root, "package")
  def managedLibDir = file(root, "maker-lib")
  def ivyFile = new File(root, ivyFileRel)
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
    //urls.foreach(println)
    new URLClassLoader(urls, null)
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

  // work in progress - incomplete tasks
  def publishLocal : BuildResult = publishLocal()
  def publishLocal(configurations : String = "default", version : String = props.Version()) =
    publishLocal_(projectAndDescendents, configurations, version)
  def publishLocalOnly : BuildResult = publishLocalOnly()
  def publishLocalOnly(configurations : String = "default", version : String = props.Version()) =
    publishLocal_(List(this), configurations, version)
  private def publishLocal_(projects : List[Project], configurations : String = "default", version : String = props.Version()) =
    QueueManager(projects, PublishLocalTask, Map("configurations"-> configurations, "version" -> version))

  def publish : BuildResult = publish()
  def publish(resolver : String = props.DefaultPublishResolver().getOrElse("default"), version : String = props.Version()) =
    publish_(projectAndDescendents, resolver, version)
  def publishOnly : BuildResult = publishOnly()
  def publishOnly(resolver : String = props.DefaultPublishResolver().getOrElse("default"), version : String = props.Version()) =
    publish_(List(this), resolver, version)
  private def publish_(projects : List[Project], resolver : String = props.DefaultPublishResolver().getOrElse("default"), version : String = props.Version()) =
    QueueManager(projects, PublishTask, Map("publishResolver" -> resolver, "version" -> version))
  
  def ~ (task : () => BuildResult) {
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
    val dependentLibs = projectAndDescendents.map(proj => proj → proj.jars.map(_.getPath))
    showGraph(makeDotFromString(dependentLibs))
  }

  import maker.utils.maven._
  import maker.utils.ivy.IvyReader
  def readIvyDependencies() : List[DependencyLib] = {
    if (ivyFile.exists())
      IvyReader.readIvyDependenciesFromFile(ivyFile)
    else {
      Log.info("No Ivy config for this module")
      Nil
    }
  }

  def readIvyResolvers() : List[MavenRepository] = {
    if (ivySettingsFile.exists())
      IvyReader.readIvyResolversFromFile(ivySettingsFile)
    else {
      Log.info("No Ivy config")
      Nil
    }
  }

  def delete = recursiveDelete(root)

  import maker.utils.maven._
  def moduleDef : ModuleDef = {
    val deps : List[DependencyLib] = readIvyDependencies()
    val repos : List[MavenRepository] = readIvyResolvers()
    val moduleLibDef = DependencyLib(name, name, props.Version(), props.Organisation(), "compile", None)
    val moduleDeps = children.map(c => DependencyLib(name, c.name, props.Version(), props.Organisation(), "compile", None))
    val projectDef = ProjectDef(description.getOrElse("Module " + name + " definition (generated by Maker)"), moduleLibDef, moduleDeps)
    ModuleDef(projectDef, deps, repos, ScmDef(props.ScmUrl(), props.ScmConnection()), props.Licenses(), props.Developers())
  }

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

    IDEAProjectGenerator.updateGitIgnoreIfRequired(this)
  }
  def downloadScalaCompilerAndLibrary{
    val ivyText="""
<ivy-module version="1.0" xmlns:e="http://ant.apache.org/ivy/extra">
  <info organisation="maker" module="maker"/>
  <configurations>
    <conf name="default" transitive="false"/>
  </configurations>
  <dependencies defaultconfmapping="*->default,sources">
    <dependency org="org.scala-lang" name="scala-compiler" rev="%s"/>
    <dependency org="org.scala-lang" name="scala-library" rev="%s"/>
  </dependencies>
</ivy-module>
    """ % (props.ScalaVersion(), props.ScalaVersion())
    val ivyFile = new File(".maker/scala-library-ivy.xml")
    FileUtils.writeToFile(
      ivyFile,
      ivyText
    )

    null
  }
}

object Project {
  def apply(name : String) : Project = Project(name, file(name))
  def apply(name : String,  libDirectories : => List[File]) : Project = Project(name, file(name), libDirs = libDirectories, props = Props())
  def apply(name : String,  libDirectories : => List[File], props : Props) : Project = Project(name, file(name), libDirs = libDirectories, props = props)
}

