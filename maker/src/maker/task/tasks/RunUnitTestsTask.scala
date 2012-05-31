package maker.task.tasks

import maker.project.Project
import maker.utils.Log
import java.lang.reflect.Modifier
import maker.utils.FileUtils
import maker.utils.os.{ScalaCommand, Command}
import maker.task.{ProjectAndTask, TaskFailed, Task}
import maker.utils.os.CommandOutputHandler
import maker.utils.FileUtils._
import maker.Maker

case object RunUnitTestsTask extends Task {

  private def isAccessibleSuite(suiteClass: java.lang.Class[_], clazz: java.lang.Class[_]): Boolean = {
    val emptyClassArray = new Array[java.lang.Class[T] forSome {type T}](0)
    try {
      suiteClass.isAssignableFrom(clazz) &&
        Modifier.isPublic(clazz.getModifiers) &&
        !Modifier.isAbstract(clazz.getModifiers) &&
        Modifier.isPublic(clazz.getConstructor(emptyClassArray: _*).getModifiers)
    }
    catch {
      case nsme: NoSuchMethodException => false
      case se: SecurityException => false
    }
  }

  private def isAccessibleSuite(className: String, loader: ClassLoader): Boolean = {
    val suiteClass = loader.loadClass("org.scalatest.Suite")
    try {
      isAccessibleSuite(suiteClass, loader.loadClass(className))
    }
    catch {
      case e: ClassNotFoundException => false
      case e: NoClassDefFoundError => false
    }
  }

  private def suiteClassNames(project: Project): List[String] = {
    val classNames = project.testClassFiles.map(FileUtils.classNameFromFile(project.testOutputDir, _))
    classNames.filter(isAccessibleSuite(_, project.classLoader)).toList
  }

  private def runnerClassAndMethod(project: Project) = {
    val runnerClass = project.classLoader.loadClass("org.scalatest.tools.Runner$")
    val cons = runnerClass.getDeclaredConstructors
    cons(0).setAccessible(true)
    val runner = cons(0).newInstance()
    val method = runnerClass.getMethod("run", classOf[Array[String]])
    (runner, method)
  }

  def exec(implicit project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    if (Maker.verboseTaskOutput && ! project.suppressTaskOutput)
      Log.info("Testing " + project)
    else
      print(".")
    recursiveDelete(project.testResultsDir)
    mkdirs(project.testResultsDir)

    val classOrSuiteNames = parameters.get("testClassOrSuiteName") match {
      case Some(names) ⇒ names.split(":").toList.filter(_.size > 0)
      case None ⇒ suiteClassNames(project)
    }
    if (classOrSuiteNames.isEmpty) {
      Log.debug("No tests found, nothing to do")
      Right(Unit)
    }
    else {
      val suiteParameters = classOrSuiteNames.map(List("-s", _)).flatten
      Log.debug("Tests to run: ")
      suiteParameters.foreach(Log.debug(_)  )
      val maxHeapMB = Runtime.getRuntime().maxMemory() / (1024 * 1024) // replicate bootstrap JVM max heap into spawned JVMs
      val systemProperties = project.props.JavaSystemProperties.asMap.map{
        case (key, value) ⇒ "-D" + key + "=" + value
      }.toList
      val opts = List("-Xmx" + maxHeapMB + "m") ::: systemProperties
      val args = (if (Maker.verboseTestOutput && !project.suppressTaskOutput) List("-o") else Nil) ::: 
        List("-c", "-u", project.testResultsDir.getAbsolutePath, "-p", project.scalatestRunpath) ::: suiteParameters
      val cmd = ScalaCommand(
        CommandOutputHandler(), 
        project.props.Java().getAbsolutePath, 
        opts,
        project.runClasspath, 
        "org.scalatest.tools.Runner", 
        args : _*
      )
      Log.debug("Executing tests in separate JVM using:\nopts = " + opts + "\n" + cmd.toString)
      cmd.exec match {
        case 0 ⇒ Right(Unit)
        case _ ⇒ {
          val failingTests = project.testResultsOnly.failed
          val failingTestsText = failingTests.map{t ⇒ t.suite + " : " + t.testName}.mkString("\n\t", "\n\t", "\n")
          Left(TaskFailed(ProjectAndTask(project, this), "Test failed in " + project + failingTestsText))
        }
      }
    }
  }
}
