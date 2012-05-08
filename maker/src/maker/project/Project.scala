package maker.project

import java.io.File
import java.lang.System
import java.util.Properties
import java.net.URLClassLoader
import maker.task._
import maker.Props
import maker.graphviz.GraphVizUtils._
import maker.graphviz.GraphVizDiGrapher._
import maker.utils._
import maker.utils.Utils._
import maker.utils.ModuleId._
import maker.utils.FileUtils._
import tasks._
import org.apache.commons.io.FileUtils._
import xml.NodeSeq

trait ProjectDef {
  def name : String
  def root : File
  def sourceDirs: List[File] = Nil
  def tstDirs: List[File] = Nil

  // use the standard maven convention dir structure as the defaults
  val mainRoot = file(root, "src/main/")
  val testRoot = file(root, "src/test/")
  val defaultSrcDirNames = "scala" :: "java" :: Nil

  val srcDirs : List[File] = if (sourceDirs.isEmpty) defaultSrcDirNames.map(file(mainRoot, _)) else sourceDirs
  val testDirs : List[File] = if (tstDirs.isEmpty) defaultSrcDirNames.map(file(testRoot, _)) else tstDirs
}

/**
 * Main maker project, defines a software build module.
 *   most params have default values and can be omitted as necessary
 */
case class Project(
      name: String,
      root: File,
      override val sourceDirs: List[File] = Nil,
      override val tstDirs: List[File] = Nil,
      libDirs: List[File] = Nil,
      providedLibDirs: List[File] = Nil, // compile time only, don't add to runtime classpath or any packaging
      managedLibDirName : String = "lib_managed",
      resourceDirs : List[File] = Nil,
      children: List[Project] = Nil,
      props : Props = Props(),
      unmanagedProperties : Properties = new Properties(),
      description : Option[String] = None,
      ivySettingsFile : File = file("ivysettings.xml"), // assumption this is typically absolute and module ivy is relative, this might be invalid?
      ivyFileRel : String = "ivy.xml",
      webAppDir : Option[File] = None,
      moduleIdentity : Option[GroupAndArtifact] = None,
      additionalLibs : List[GAV] = Nil,
      additionalExcludedLibs : List[GAV] = Nil,
      providedLibs : List[String] = Nil) extends ProjectDef {

  val testResultsDir = file(root, ".maker/test-results")
  val outputDir = file(root, "classes")
  val javaOutputDir = file(root, "java-classes")
  val testOutputDir = file(root, "test-classes")
  val packageDir = file(root, "package")
  val managedLibDir = file(root, managedLibDirName)
  val ivyFile = new File(root, ivyFileRel)
  def ivyGeneratedFile : Option[File] = {
    val genFile = file(root, nameAndExt(ivyFile)._1 + "-dynamic.ivy")
    if (ivyFile.exists) {
      copyFile(ivyFile, genFile)
      val includes : List[NodeSeq] = allDeps.flatMap(_.additionalLibs).map(_.toIvyInclude)
      val excludes : List[NodeSeq] = allDeps.map(_.moduleId.toIvyExclude) ::: allDeps.flatMap(_.additionalExcludedLibs.map(_.toIvyExclude))
      replaceInFile(genFile, "${maker.module.excluded.libs}", (includes ::: excludes).mkString("\n"))
      Some(genFile)
    }
    else None
  }
  val makerDirectory = mkdirs(new File(root, ".maker"))

  val jarDirs : List[File] = if (libDirs.isEmpty) List(file(root, "lib"), managedLibDir) else libDirs
  val moduleId : GroupAndArtifact = if (moduleIdentity.isEmpty) props.GroupId() % name else moduleIdentity.get
  val packagingRoot = file(root, "package")

  // convenience copy functions
  def dependsOn(projects: Project*) = copy(children = children ::: projects.toList)
  def withResourceDirs(dirs : List[File]) : Project = copy(resourceDirs = dirs)
  def withResourceDirs(dirs : String*) : Project = withResourceDirs(dirs.map(d => file(root, d)).toList)
  def withAdditionalSourceDirs(dirs : String*) = copy(sourceDirs = dirs.toList.map(d => file(root, d)) ::: this.sourceDirs)
  def withAdditionalTestDirs(dirs : String*) = copy(tstDirs = dirs.toList.map(d => file(root, d)) ::: this.tstDirs)
  def withAdditionalLibs(libs : GAV*) = copy(additionalLibs = libs.toList ::: this.additionalLibs)
  def withProvidedLibDirs(dirs : String*) = copy(providedLibDirs = dirs.toList.map(d => file(root, d)) ::: this.providedLibDirs)
  def setAdditionalExcludedLibs(libs : GAV*) = copy(additionalExcludedLibs = libs.toList)
  def withProvidedLibs(libNames : String*) = copy(providedLibs = libNames.toList ::: this.providedLibs)

  def srcFiles() = findSourceFiles(srcDirs: _*)
  def testSrcFiles() = findSourceFiles(testDirs: _*)
  def javaSrcFiles() = findJavaSourceFiles(srcDirs: _*)

  def classFiles = findClasses(outputDir)
  def javaClassFiles = findClasses(javaOutputDir)
  def testClassFiles = findClasses(testOutputDir)

  def jars = findJars(jarDirs: _*).toList.sortWith(_.getPath < _.getPath)
  def scalaLibs = List(file(props.ScalaHome(), "lib/scala-compiler.jar"), file(props.ScalaHome(), "lib/scala-library.jar"))

  def classpathDirectoriesAndJars : List[File] = ((outputDir :: javaOutputDir :: testOutputDir :: (jars ::: scalaLibs)) ::: resourceDirs ::: children.flatMap(_.classpathDirectoriesAndJars)).distinct
  def compilationClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")
  def runClasspath = classpathDirectoriesAndJars.map(_.getAbsolutePath).mkString(":")
  def scalatestRunpath = classpathDirectoriesAndJars.mkString(" ")
  def printClasspath = compilationClasspath.split(":").foreach(println)

  def classLoader = {
    val urls = classpathDirectoriesAndJars.map(_.toURI.toURL).toArray
    new URLClassLoader(urls, null)
  }

  def outputArtifact = file(packageDir.getAbsolutePath, name + (webAppDir match {
    case None => ".jar"
    case _ => ".war"
  }))

  val state = ProjectState(this)
  val compilers = ProjectCompilers(this)
  val dependencies = ProjectDependencies(this)

  def projectAndDescendents = this :: dependencies.descendents.toList

  /**********************
    Tasks
  **********************/
  def mkTask(t : Task) = ProjectAndTask(this, t)

  def clean = TaskManager(projectAndDescendents, CleanTask)
  def cleanOnly = TaskManager(List(this), CleanTask)

  def compile = TaskManager(projectAndDescendents, CompileSourceTask)

  def testCompile = TaskManager(projectAndDescendents, CompileTestsTask)
  def test = TaskManager(projectAndDescendents, RunUnitTestsTask)
  def testOnly = TaskManager(List(this), RunUnitTestsTask)
  def testClassOnly(testClassNames : String*) = TaskManager(List(this), RunUnitTestsTask, Map("testClassOrSuiteName" -> testClassNames.mkString(":")))

  def testResults = ScalatestResults(this)
  def pack = TaskManager(projectAndDescendents, PackageTask)
  def packOnly = TaskManager(List(this), PackageTask)

  def withDefaultConfig = withNelDefault("default") _

  def update : BuildResult[AnyRef] = update("default")
  def update(configurations : String*) = TaskManager(projectAndDescendents, UpdateTask, Map("configurations" -> withDefaultConfig(configurations.toList).mkString(":")))
  def updateOnly : BuildResult[AnyRef] = updateOnly("default")
  def updateOnly(configurations : String*) = TaskManager(List(this), UpdateTask, Map("configurations" -> withDefaultConfig(configurations.toList).mkString(":")))

  def publishLocal : BuildResult[AnyRef] = publishLocal()
  def publishLocal(configurations : String = "default", version : String = props.Version()) =
    publishLocal_(projectAndDescendents, configurations, version)
  def publishLocalOnly : BuildResult[AnyRef] = publishLocalOnly()
  def publishLocalOnly(configurations : String = "default", version : String = props.Version()) =
    publishLocal_(List(this), configurations, version)
  private def publishLocal_(projects : List[Project], configurations : String = "default", version : String = props.Version()) =
    TaskManager(projects, PublishLocalTask, Map("configurations"-> configurations, "version" -> version))

  def publish : BuildResult[AnyRef] = publish()
  def publish(resolver : String = props.DefaultPublishResolver().getOrElse("default"), version : String = props.Version()) =
    publish_(projectAndDescendents, resolver, version)
  def publishOnly : BuildResult[AnyRef] = publishOnly()
  def publishOnly(resolver : String = props.DefaultPublishResolver().getOrElse("default"), version : String = props.Version()) =
    publish_(List(this), resolver, version)
  private def publish_(projects : List[Project], resolver : String = props.DefaultPublishResolver().getOrElse("default"), version : String = props.Version()) =
    TaskManager(projects, PublishTask, Map("publishResolver" -> resolver, "version" -> version))

  def runMain(className : String)(opts : String*)(args : String*) = {
    val r = TaskManager(List(this), RunMainTask, Map("mainClassName" -> className, "opts" -> opts.mkString("|") , "args" -> args.mkString("|")))
    println("runMain task completed for class: " + className)
    r
  }

  def runJetty : BuildResult[AnyRef] = runJetty()
  def runJetty(portNo : Int = 8080) = {
    val r = TaskManager(List(this), RunJettyTask, Map("portNo" -> portNo.toString))
    println("runJetty task completed")
    r
  }

  def cleanManagedLibs = {
    Option(managedLibDir.listFiles).map(_.foreach(_.delete))
    ivyGeneratedFile.map(f => if (f.exists) f.delete) // delete this as well since it's regenerated on the fly during update
  }

  def cleanAllManagedLibs =
    projectAndDescendents.map(_.cleanManagedLibs)

  def cleanAllOnly = {
    cleanManagedLibs
    cleanOnly
  }

  def cleanAll = {
    cleanAllManagedLibs
    clean
  }

  def delete = recursiveDelete(root)

  def findLibs(libName : String) =
    classpathDirectoriesAndJars.filter(f => f.getName.contains(libName)).foreach(println)

  def ~ (task : () => BuildResult[AnyRef]) {
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
    while (true) {
      Thread.sleep(1000)
      if (System.in.available  > 0 && System.in.read == 10) return
      if (anySourceFileHasChanged) {
        (lastTaskTime,  lastSrcModificationTime) match {
          case (None, _) => { rerunTask }                       // Task has never been run
          case (Some(t1), Some(t2)) if t1 < t2 => { rerunTask } // Code has changed since task last run
          case _ =>                                             // Either no code yet or code has not changed
        }
      }
    }
  }

  // utils and project / dependency related queries
  def allDeps : List[Project] = this :: children.flatMap(_.allDeps).sortWith(_.name < _.name)
  def isDependentOn(project : Project) = allDeps.exists(p => p == project)
  def dependsOnPaths(project : Project) : List[List[Project]] = {
    def depends(currentProject : Project, currentPath : List[Project], allPaths : List[List[Project]]) : List[List[Project]] = {
      if (project == currentProject) (currentProject :: currentPath).reverse :: allPaths
      else {
        currentProject.children match {
          case Nil => Nil
          case ps => ps.flatMap(p => depends(p, currentProject :: currentPath, allPaths))
        }
      }
    }
    depends(this, Nil, Nil)
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

  // some ivy/maven related utils
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

  def hasScala = findFilesWithExtension("scala", srcDirs : _*).size > 0

  // maven / ivy integration
  import maker.utils.maven._
  def moduleDef : ModuleDef = {
    val deps : List[DependencyLib] = readIvyDependencies()
    val repos : List[MavenRepository] = readIvyResolvers()
    val moduleLibDef = DependencyLib(name, moduleId % props.Version(), "compile", None)
    val moduleDeps = {
      val childLibs = children.map(c => DependencyLib(name, c.moduleId % props.Version(), "compile", None))
      if (hasScala)
        DependencyLib(name, "org.scala-lang" % "scala-library" % props.ScalaVersion(), "compile", None) :: childLibs
      else
        childLibs
    }
    val projectDef = ProjectDef(description.getOrElse("Module " + name + " definition (generated by Maker)"), moduleLibDef, moduleDeps)
    ModuleDef(projectDef, deps, repos, ScmDef(props.ScmUrl(), props.ScmConnection()), props.Licenses(), props.Developers())
  }

  override def toString = "Project " + moduleId.toString
}

case class ProjectLib(projectName:String, exported:Boolean)

class TopLevelProject(name:String,
                      children:List[Project],
                      props:Props = Props(),
                      scalaSwingLibraryProjects:List[ProjectLib] = Nil) extends Project(name, file("."), Nil, Nil, Nil, resourceDirs = Nil, children = children, props = props) {

  def generateIDEAProject() {
    val allModules = children.flatMap(_.projectAndDescendents).distinct

    val swingLibraryRequired = scalaSwingLibraryProjects.nonEmpty

    IDEAProjectGenerator.generateTopLevelModule(root, name)
    IDEAProjectGenerator.generateIDEAProjectDir(root, name, swingLibraryRequired)
    allModules.foreach(module => IDEAProjectGenerator.generateModule(
      module,
      scalaSwingLibraryProjects.find(_.projectName == module.name)))

    IDEAProjectGenerator.generateModulesFile(file(root, ".idea"), (this :: allModules).map(_.name))

    IDEAProjectGenerator.updateGitIgnoreIfRequired(this)
  }
  def downloadScalaCompilerAndLibrary{
    import maker.utils.RichString._
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
