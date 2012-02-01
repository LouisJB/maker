package maker.task

import maker.project.Project

case class TaskFailed(task : ProjectAndTask, reason : String)

trait Task{
  def exec(project : Project, acc : List[AnyRef] = Nil) : Either[TaskFailed, AnyRef]
}

object Task {
  lazy val standardWithinProjectDependencies = Map[Task, Set[Task]](
    CompileSourceTask -> Set(CompileJavaSourceTask),
    CompileTestsTask -> Set(CompileSourceTask),
    RunUnitTestsTask -> Set(CompileTestsTask)
  )
  lazy val standardDependentProjectDependencies = Map[Task, Set[Task]](
    CompileSourceTask -> Set(CompileSourceTask),
    CompileTestsTask -> Set(CompileTestsTask),
    CompileJavaSourceTask → Set(CompileSourceTask)
  )
}
