package maker.task

import maker.project.Project
import maker.utils.Log
import org.scalatest.Suite
import java.lang.reflect.Modifier
import maker.utils.FileUtils

case object RunUnitTestsTask extends Task{

  private def isAccessibleSuite(suiteClass: java.lang.Class[_], clazz: java.lang.Class[_]): Boolean = {
    val emptyClassArray = new Array[java.lang.Class[T] forSome { type T }](0)
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

  private def suiteClassNames(project : Project, loader : ClassLoader) : List[String] = {
    val classNames = project.testClassFiles.map(FileUtils.classNameFromFile(project.testOutputDir, _))
    val validClasses = classNames.filter(isAccessibleSuite(_, loader)).toList
    println("valid classes " + validClasses.size)
    validClasses
  }
  
  private def runnerClassAndMethod(project : Project, loader : ClassLoader) = {
    val runnerClass = loader.loadClass("org.scalatest.tools.Runner$")
      //val fileClass = project.classLoader.loadClass("java.io.File")
    //println("file class " + fileClass)
    //val testListenerClass = project.classLoader.loadClass("org.testng.ITestListener")
    //Log.info("Test listener " + testListenerClass)
    val cons = runnerClass.getDeclaredConstructors
    cons(0).setAccessible(true)
    val runner = cons(0).newInstance()
    val method = runnerClass.getMethod("run", classOf[Array[String]])
    (runner, method)
  }

  def allThreads : Set[Thread] = {
    import scala.collection.JavaConversions._
    Set[Thread]() ++ Thread.getAllStackTraces().keySet()
  }

//  def printThread(thread : Thread){
//    Log.info("\n\n\nThread " + thread.getId)
//    Log.info(thread.getStackTrace.toList.mkString("\n"))
//  }
  def exec(project : Project, acc : List[AnyRef]) = {
    val loader = project.classLoader
    Log.info("Testing " + project)
    val suiteParameters = suiteClassNames(project, loader).map(List("-s", _)).flatten
//    val threadIds = allThreads.map(_.getId)

    var result : Either[TaskFailed, AnyRef] = null
    val t = new Thread(new Runnable()
    {
        def run()
        {
          result = if (suiteParameters.isEmpty){
            Right(Unit)
          } else {
            val (runner, method) = runnerClassAndMethod(project, loader)
              //val pars = List("-c", "-o", "-p", project.scalatestRunpath) ::: suiteParameters
            val pars = List("-o", "-p", project.scalatestRunpath) ::: suiteParameters
            val result = method.invoke(runner, pars.toArray).asInstanceOf[Boolean]
            if (result)
              Right(Unit)
            else
              Left(TaskFailed(ProjectAndTask(project, RunUnitTestsTask.this), "Test failed in " + project))
          }

        }
    })
    t.start()
    t.join()
//    Thread.sleep(2000)
//    val newThreads = allThreads.filterNot{thread => threadIds.contains(thread.getId)}
////    println("There are " + newThreads.size + ", new threads")
////    println("There were originally " + threadIds.size + " threads")
//    newThreads.foreach{
//      thread =>
////        printThread(thread)
//        thread.stop
//    }
//    Thread.sleep(2000)
//    val remainingThreads = allThreads.filterNot{thread => threadIds.contains(thread.getId)}
//    println("After delete there are " + remainingThreads.size + " threads")
//    val finalizerThreads = allThreads.filter{
//      thread =>
//        thread.getStackTrace.toList.mkString("\n").contains("com.google.common.base.internal.Finalizer")
//    }
////    println("Finalizer threads")
////    finalizerThreads.foreach(printThread)
    java.util.ResourceBundle.clearCache(loader);
//    LogFactory.release(loader);
    java.beans.Introspector.flushCaches();
    result
  }
}

