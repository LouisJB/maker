package maker.task
import org.scalatest.tools.Runner
import maker.project.Project
import maker.utils.Log

case class Test(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[Unit]{

  val lock = new Object
  def dependsOn(tasks : Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)
  
  def execSelf = {
    Log.info("Testing " + project)
    if (Runner.run(Array("-o", "-p", project.testOutputDir.getAbsolutePath)))
      Right(Unit)
    else
      Left(TaskFailed(this, "Bad test"))
  }
}
