package maker.task.tasks

import maker.task.Task
import maker.project.Project
import maker.task.TaskFailed

case object RunFailingTestsTask extends Task{
  def exec(implicit project : Project, acc : List[AnyRef] = Nil, parameters : Map[String, String]) : Either[TaskFailed, AnyRef] = {
    val failedSuites = project.testResultsOnly.failed.map(_.suite).mkString(":")
    RunUnitTestsTask.exec(
      project,
      acc,
      parameters + ("testClassOrSuiteName" → failedSuites)
    )
  }
}

