package maker.project

import maker.utils.FileUtils._
import java.io.File
import maker.utils.FileUtils
import collection.mutable.ListBuffer

object IDEAProjectGenerator {
  def generateTopLevelModule(rootDir:File, name:String) {
    val content = """<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$" />
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
  </component>
</module>
"""

    writeToFile(file(rootDir, name + ".iml"), content)
  }

  def generateIDEAProjectDir(rootDir:File, name:String, swingLibraryRequired:Boolean) {
    val ideaProjectRoot = mkdirs(file(rootDir, ".idea"))
    writeToFile(file(ideaProjectRoot, ".name"), name)

    val librariesDir = mkdirs(file(ideaProjectRoot, "libraries"))
    val scalaLibraryContent = """<component name="libraryTable">
  <library name="scala-library-2.9.1">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/lib/scala/lib_managed/scala-library-jar-2.9.1.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
"""
    writeToFile(file(librariesDir, "scala_library_2_9_1.xml"), scalaLibraryContent)

    val scalaCompilerLibraryContent = """<component name="libraryTable">
  <library name="scala-compiler-2.9.1">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/lib/scala/lib_managed/scala-compiler-jar-2.9.1.jar!/" />
      <root url="jar://$PROJECT_DIR$/lib/scala/lib_managed/scala-library-jar-2.9.1.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
"""
    writeToFile(file(librariesDir, "scala_compiler_2_9_1.xml"), scalaCompilerLibraryContent)

    val scalaCompilerContent = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ScalacSettings">
    <option name="COMPILER_LIBRARY_NAME" value="scala-compiler-2.9.1" />
    <option name="COMPILER_LIBRARY_LEVEL" value="Project" />
    <option name="MAXIMUM_HEAP_SIZE" value="2048" />
    <option name="FSC_OPTIONS" value="-max-idle 0" />
  </component>
</project>
"""
    writeToFile(file(ideaProjectRoot, "scala_compiler.xml"), scalaCompilerContent)

    val miscContent = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectRootManager" version="2" languageLevel="JDK_1_6" assert-keyword="true" jdk-15="true" project-jdk-name="JDK6" project-jdk-type="JavaSDK">
    <output url="file://$PROJECT_DIR$/out" />
  </component>
</project>
"""
    writeToFile(file(ideaProjectRoot, "misc.xml"), miscContent)

    val highlightingContent = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="HighlightingAdvisor">
    <option name="SUGGEST_TYPE_AWARE_HIGHLIGHTING" value="false" />
    <option name="TYPE_AWARE_HIGHLIGHTING_ENABLED" value="true" />
  </component>
</project>
"""
    writeToFile(file(ideaProjectRoot, "highlighting.xml"), highlightingContent)

    if (swingLibraryRequired) {
      val swingLibraryContent = """<component name="libraryTable">
  <library name="scala-swing-library-2.9.1">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/lib/scala/lib_managed/scala-swing-jar-2.9.1.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
"""
      writeToFile(file(librariesDir, "scala_swing_library_2_9_1.xml"), swingLibraryContent)
    }
  }

  def generateModule(project:Project, scalaSwingProjectLib:Option[ProjectLib]) {
    val sources = if (project.srcDirs.isEmpty && project.resourceDirs.isEmpty && project.testDirs.isEmpty) {
        """    <content url="file://$MODULE_DIR$" />"""
    } else {
      def sourceFolder(dir:File, test:Boolean=false) = {
        val relDir = FileUtils.relativise(project.root, dir)
        """      <sourceFolder url="file://$MODULE_DIR$/%s" isTestSource="%s" />""".format(relDir, test.toString)
      }
      val sourcesAndResources = (project.srcDirs ::: project.resourceDirs).map(sourceFolder(_, false))
      val testSources = project.testDirs.map(sourceFolder(_, true))
      val allSources = (sourcesAndResources ::: testSources).mkString("\n")
      """    <content url="file://$MODULE_DIR$">
%s
    </content>""".format(allSources)
    }

    def libraryEntry(jarEntry:String) = """    <orderEntry type="module-library" exported="">
      <library>
        <CLASSES>
%s
        </CLASSES>
        <JAVADOC />
        <SOURCES />
      </library>
    </orderEntry>""".format(jarEntry)
    def jarEntry0(jarName:String) = """          <root url="jar://$MODULE_DIR$/maker-lib/%s!/" />""".format(jarName)
    val libraryDependencies = project.jars.map(file => {
      val jarName = file.getName
      val jarEntry = jarEntry0(jarName)
      libraryEntry(jarEntry)
    }).mkString("\n")

    def moduleDependency(module:String) = """<orderEntry type="module" module-name="%s" exported="" />""".format(module)
    val moduleDependencies = project.children.map(project => moduleDependency(project.name)).mkString("\n")

    val dependencies = moduleDependencies + libraryDependencies

    val output = {
      val relativeOutputDir = FileUtils.relativise(project.root, project.outputDir)
      val relativeTestOutputDir = FileUtils.relativise(project.root, project.testOutputDir)
      """    <output url="file://$MODULE_DIR$/%s" />
    <output-test url="file://$MODULE_DIR$/%s" />""".format(relativeOutputDir, relativeTestOutputDir)
    }

    val swingLibraryEntry = scalaSwingProjectLib match {
      case Some(pl) => """    <orderEntry type="library" %sname="scala-swing-library-2.9.1" level="project" />""".format((if (pl.exported)"""exported="" """ else ""))
      case _ => ""
    }

    val moduleContent = """<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="FacetManager">
    <facet type="scala" name="Scala">
      <configuration>
        <option name="compilerLibraryLevel" value="Project" />
        <option name="compilerLibraryName" value="scala-compiler-2.9.1" />
        <option name="fsc" value="true" />
      </configuration>
    </facet>
  </component>
  <component name="NewModuleRootManager" inherit-compiler-output="false">
%s
    <exclude-output />
%s
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
    <orderEntry type="library" name="scala-library-2.9.1" level="project" />
%s
%s
  </component>
</module>
""".format(output, sources, swingLibraryEntry, dependencies)
    writeToFile(file(project.root, project.name + ".iml"), moduleContent)
  }

  def generateModulesFile(ideaProjectRoot:File, modules:List[String]) {
    def moduleEntry(moduleName:String) = """<module fileurl="file://$PROJECT_DIR$/%s" filepath="$PROJECT_DIR$/%s" />""".format(moduleName, moduleName)
    val moduleEntries = (modules match {
      case head :: tail => {
        moduleEntry(head + ".iml") :: tail.map(module => moduleEntry(module + "/" + module + ".iml"))
      }
      case _ => Nil
    }).mkString("\n")

    val modulesContent = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
      %s
    </modules>
  </component>
</project>
""".format(moduleEntries)
    writeToFile(file(ideaProjectRoot, "modules.xml"), modulesContent)
  }

  def updateGitIgnoreIfRequired(project:Project) {
    val gitIgnoreFile = file(project.root, ".gitignore")
    if (gitIgnoreFile.exists()) {
      val gitIgnoreLines = gitIgnoreFile.read.map(_.replaceAll("\n", "").trim).toList
      val linesToWrite = new ListBuffer[String]()
      val ideaProjectFolder = ".idea"
      if (!gitIgnoreLines.contains(ideaProjectFolder)) {
        linesToWrite += ideaProjectFolder
      }
      val moduleFiles = "*.iml"
      if (!gitIgnoreLines.contains(moduleFiles)) {
        linesToWrite += moduleFiles
      }
      linesToWrite.foreach(line => gitIgnoreFile.append(line))
    }
  }
}
