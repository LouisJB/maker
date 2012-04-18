package maker.task

import maker.project.Project
import tasks._

case class TaskFailed(task : ProjectAndTask, reason : String)

trait Task {
  def exec(project : Project, acc : List[AnyRef] = Nil, parameters : Map[String, String]) : Either[TaskFailed, AnyRef]
}

object Task {
  lazy val standardWithinProjectDependencies = Map[Task, Set[Task]](
    CompileJavaSourceTask -> Set(CompileSourceTask),
    CompileTestsTask -> Set(CompileJavaSourceTask),
    PackageTask -> Set(CompileJavaSourceTask),
    PublishLocalTask -> Set(PackageTask),
    PublishTask -> Set(PublishLocalTask),
    RunUnitTestsTask -> Set(CompileTestsTask),
    RunMainTask -> Set(CompileJavaSourceTask)
  )
  lazy val standardDependentProjectDependencies = Map[Task, Set[Task]](
    CompileTestsTask -> Set(CompileTestsTask),
    CompileSourceTask → Set(CompileJavaSourceTask)
  )
}
