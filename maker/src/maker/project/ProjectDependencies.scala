package maker.project

import maker.task._
import maker.utils.Memoize1

case class ProjectDependencies(project : Project){
  def descendents : Set[Project] = {
    project.children.toSet ++ project.children.flatMap(_.dependencies.descendents)
  }

  /**
   * The tasks that need to be run WITHIN this project before the given task is run
   */
  def childTasksWithinProject(task : Task) : Set[Task] =
    Task.standardWithinProjectDependencies(project).getOrElse(task, Set[Task]()) //.filterNot{tsk=> project.javaSrcFiles.isEmpty && tsk == CompileJavaSourceTask}

  /**
   * The tasks that need to be run IN CHILD PROJECTS before the given task is run
   */
  def childTasksInChildProjects(task : Task) : Set[Task] = Task.standardDependentProjectDependencies.getOrElse(task, Set())

  lazy val childProjectTasks = 
    Memoize1((task : Task) => {
      val withinProjectDeps : Set[ProjectAndTask] = childTasksWithinProject(task).map(ProjectAndTask(project, _)) 
      val childProjectDependencies : Set[ProjectAndTask] = project.children.flatMap{p => childTasksInChildProjects(task).map{t => ProjectAndTask(p, t)}}.toSet
      withinProjectDeps ++ childProjectDependencies
    })
}
