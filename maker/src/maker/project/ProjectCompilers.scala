package maker.project

import tools.nsc.{Settings, Global}
import tools.nsc.reporters.ConsoleReporter
import tools.nsc.io.{Directory, PlainDirectory}
import plugin._
import java.io.PrintWriter
import maker.utils.FileUtils._

case class ProjectCompilers(project : Project){
  private def makeCompiler(isTestCompiler : Boolean) = {
    val settings = new Settings
    val reporter = new ConsoleReporter(settings, Console.in, new PrintWriter(project.props.CompilationOutputStream, true))

    val scalaLibs = findJars(file(project.props.ScalaHome(), "lib")).toList.mkString(":")
    settings.usejavacp.value = false
    val compilerOutputDir = if (isTestCompiler) project.testOutputDir else project.outputDir
    compilerOutputDir.mkdirs
    //settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(compilerOutputDir)))
    settings.d.value_= (new PlainDirectory(new Directory(compilerOutputDir)).path)
    settings.bootclasspath.value = scalaLibs
    settings.classpath.value = project.compilationClasspath

    new scala.tools.util.PathResolver(settings).result
    val comp = new Global(settings, reporter) {
      self =>
      override protected def computeInternalPhases() {
        super.computeInternalPhases
        //phasesSet += new GenerateCompilationMetadata(self, project.state.signatures, project.state.classFileDependencies, compilerOutputDir, project.state.sourceToClassFiles).Component
        phasesSet += new CollectProjectSignatures(self, project.state.signatures)
        phasesSet += new FileDependencyAnalyzer(self, ProjectFileDependencies(project.makerDirectory))
      }
    }
    comp
  }


  //private val compiler_ : Global = makeCompiler(isTestCompiler = false)
  def compiler : Global = {
    val compiler_ : Global = makeCompiler(isTestCompiler = false)
    compiler_.settings.classpath.value = project.compilationClasspath
    compiler_
  }
  //private val testCompiler_ : Global = makeCompiler(isTestCompiler = true)
  def testCompiler : Global = {
    val testCompiler_ : Global = makeCompiler(isTestCompiler = true)
    testCompiler_.settings.classpath.value = project.compilationClasspath
    testCompiler_
  }
}
