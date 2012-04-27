package maker.task.tasks

import maker.project.Project
import maker.utils.Log
import java.lang.reflect.Modifier
import maker.utils.FileUtils
import maker.os.Command
import maker.task.{ProjectAndTask, TaskFailed, Task}

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
    try {
      val suiteClass = loader.loadClass("org.scalatest.Suite")
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

  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    Log.info("Testing " + project)
    val classOrSuiteNames = parameters.get("testClassOrSuiteName").map(_.split(":").toList.filter(_.size > 0)).getOrElse(Nil)
    val suiteParameters = classOrSuiteNames match {
      case Nil => suiteClassNames(project).map(List("-s", _)).flatten
      case _ => classOrSuiteNames.map(List("-s", _)).flatten
    }
    if (suiteParameters.isEmpty) {
      Log.info("No tests found, nothing to do")
      Right(Unit)
    }
    else {
      Log.info("Tests to run: ")
      suiteParameters.foreach(Log.debug(_)  )
      val args = List(
        project.props.JavaHome() + "/bin/java",
        "-Dscala.usejavacp=true",
        "-classpath",
        project.runClasspath,
        "scala.tools.nsc.MainGenericRunner",
        "org.scalatest.tools.Runner") ::: (List("-c", "-o", "-p", project.scalatestRunpath) ::: suiteParameters)

      val cmd = Command(args: _*)
      println(cmd)
      val (result, msg) = cmd.exec()

      if (result == 0)
        Right(Unit)
      else
        Left(TaskFailed(ProjectAndTask(project, this), "Test failed in " + project))
    }
  }

  def exec_old(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    Log.info("Testing " + project)
    val suiteParameters = suiteClassNames(project).map(List("-s", _)).flatten
    if (suiteParameters.isEmpty) {
      Right(Unit)
    } else {
      val (runner, method) = runnerClassAndMethod(project)
      val pars = List("-c", "-o", "-p", project.scalatestRunpath) ::: suiteParameters
      val result = method.invoke(runner, pars.toArray).asInstanceOf[Boolean]
      if (result)
        Right(Unit)
      else
        Left(TaskFailed(ProjectAndTask(project, this), "Test failed in " + project))
    }
  }
}

