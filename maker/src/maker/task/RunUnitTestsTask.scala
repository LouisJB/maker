package maker.task

import maker.project.Project
import maker.utils.Log

case object RunUnitTestsTask extends Task{

  def exec(project : Project, acc : List[AnyRef]) = {
    Log.info("Testing " + project)
    //val path = project.testOutputDir.getAbsolutePath + " " + project.outputDir.getAbsolutePath 
    val path = project.scalatestRunpath
    val classLoader = project.classLoader
    Log.info("Class loader = " + classLoader)
    val runnerClass = classLoader.loadClass("org.scalatest.tools.Runner$")
      val cons = runnerClass.getDeclaredConstructors
    cons(0).setAccessible(true)
    val runner = cons(0).newInstance()
    val method = runnerClass.getMethod("run", classOf[Array[String]])
    val pars = Array("-c", "-o", "-p", path)
    Log.info("Test parameters are " + pars.toList)
    val result = method.invoke(runner, pars).asInstanceOf[Boolean]
    if (result)
      Right(Unit)
    else
      Left(TaskFailed(ProjectAndTask(project, this), "Test failed in " + project))
  }
}

