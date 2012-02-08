package maker.project

import tools.nsc.{Settings, Global}
import tools.nsc.reporters.ConsoleReporter
import tools.nsc.io.{Directory, PlainDirectory}
import plugin._

case class ProjectCompilers(project : Project){
  private def makeCompiler(isTestCompiler : Boolean) = {
    val settings = new Settings
    val reporter = new ConsoleReporter(settings)
    val scalaAndJavaLibs = System.getProperty("sun.boot.class.path")

    settings.usejavacp.value = false
    val compilerOutputDir = if (isTestCompiler) project.testOutputDir else project.outputDir
    settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(compilerOutputDir)))
    settings.javabootclasspath.value = scalaAndJavaLibs
    settings.classpath.value = project.compilationClasspath

    new scala.tools.util.PathResolver(settings).result
    val comp = new Global(settings, reporter) {
      self =>
      override protected def computeInternalPhases() {
        super.computeInternalPhases
        phasesSet += new GenerateCompilationMetadata(self, project.state.signatures, project.state.classFileDependencies, compilerOutputDir, project.state.sourceToClassFiles).Component
      }
    }
    comp
  }


  private val compiler_ : Global = makeCompiler(isTestCompiler = false)
  def compiler : Global = {
    compiler_.settings.classpath.value = project.compilationClasspath
    compiler_
  }
  private val testCompiler_ : Global = makeCompiler(isTestCompiler = true)
  def testCompiler : Global = {
    testCompiler_.settings.classpath.value = project.compilationClasspath
    testCompiler_
  }
}
