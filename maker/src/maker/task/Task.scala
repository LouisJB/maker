package maker.task

import maker.project.Project
import maker.utils.Memoize1
import tasks._
import maker.utils.RichString._

case class TaskFailed(task : ProjectAndTask, reason : String){
  override def toString = { 
"""Failure
     Project: %s
     Task   : %s

     %s
""" %(task.project, task.task, reason)
}
}
  

trait Task {
  def exec(project : Project, acc : List[AnyRef] = Nil, parameters : Map[String, String]) : Either[TaskFailed, AnyRef]
}

object Task {
  lazy val standardFixedWithinProjectDependencies = Map[Task, Set[Task]](
    CompileTestsTask -> Set(CompileSourceTask),
    PackageTask -> Set(CompileSourceTask),
    PublishLocalTask -> Set(PackageTask),
    PublishTask -> Set(PublishLocalTask),
    RunUnitTestsTask -> Set(CompileTestsTask),
    RunFailingTestsTask -> Set(CompileTestsTask),
    RunMainTask -> Set(CompileSourceTask),
    RunJettyTask -> Set(PackageTask),
    DocTask -> Set(CompileSourceTask)
  )

  lazy val standardWithinProjectDependencies : Project => Map[Task, Set[Task]] = Memoize1((project : Project) => {
    if (project.props.UpdateOnCompile()) standardFixedWithinProjectDependencies + (CompileSourceTask -> Set(UpdateTask)) else standardFixedWithinProjectDependencies
  })

  lazy val standardDependentProjectDependencies = Map[Task, Set[Task]](
    CompileTestsTask -> Set(CompileTestsTask),
    CompileSourceTask → Set(CompileSourceTask)
  )

  val termSym = "ctrl-]"
  val termChar = 29 // ctrl-]
}
