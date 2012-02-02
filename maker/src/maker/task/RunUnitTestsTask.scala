package maker.task

import maker.project.Project
import maker.utils.Log
import org.scalatest.Suite
import java.lang.reflect.Modifier
import maker.utils.FileUtils

case object RunUnitTestsTask extends Task{
  private val emptyClassArray = new Array[java.lang.Class[T] forSome { type T }](0)

  private def isAccessibleSuite(suiteClass: java.lang.Class[_], clazz: java.lang.Class[_]): Boolean = {
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

  private def suiteClassNames(project : Project) : List[String] = {
    val classNames = project.testClassFiles.map(FileUtils.classNameFromFile(project.testOutputDir, _))
    classNames.foreach(println)
    classNames.filter(isAccessibleSuite(_, project.classLoader)).toList
  }
  
  private def runnerClassAndMethod(project : Project) = {
    val runnerClass = project.classLoader.loadClass("org.scalatest.tools.Runner$")
    val cons = runnerClass.getDeclaredConstructors
    cons(0).setAccessible(true)
    val runner = cons(0).newInstance()
    val method = runnerClass.getMethod("run", classOf[Array[String]])
    (runner, method)
  }

  def exec(project : Project, acc : List[AnyRef]) = {
    Log.info("Testing " + project)
    val (runner, method) = runnerClassAndMethod(project)
    val suiteParameters = suiteClassNames(project).map(List("-s", _)).flatten
    val pars = List("-c", "-o", "-p", project.scalatestRunpath) ::: suiteParameters
    val result = method.invoke(runner, pars.toArray).asInstanceOf[Boolean]
    if (result)
      Right(Unit)
    else
      Left(TaskFailed(ProjectAndTask(project, this), "Test failed in " + project))
  }
}

