package maker.task

import maker.project.Project
import maker.utils.{Stopwatch, Log}
import maker.Maker

object ProjectAndTask {
  val lock = new Object
  var runningTasks = List[ProjectAndTask]()

  private def reportTasks {
    lock.synchronized {
      //println("There are " + runningTasks.size + " task(s) currently running")
      //println(runningTasks.mkString("\t", "\n\t", "\n"))
    }
  }

  def addTask(pt: ProjectAndTask) {
    lock.synchronized {
      //println("Adding task " + pt)
      runningTasks = pt :: runningTasks
      reportTasks
    }
  }

  def removeTask(pt: ProjectAndTask) {
    lock.synchronized {
      //println("Removing task " + pt)
      runningTasks = runningTasks.filterNot(_ == pt)
      reportTasks
    }
  }

  def getTaskTree(p: Project, task: Task): List[(ProjectAndTask, List[ProjectAndTask])] = {
    def recurse(acc: Set[(ProjectAndTask, List[ProjectAndTask])]): Set[(ProjectAndTask, List[ProjectAndTask])] = {
      val newElements: Set[ProjectAndTask] = acc.flatMap(_._2).filterNot(acc.map(_._1))
      if (newElements.isEmpty)
        acc
      else
        recurse(acc ++ newElements.map {
          pt => pt → pt.immediateDependencies.toList
        }.toSet)
    }
    recurse(Set(ProjectAndTask(p, task) → p.dependencies.childProjectTasks(task).toList)).toList
    //(ProjectAndTask(p, task) -> p.dependencies.descendents.map{proj => proj.dependencies.childTasksInChildProjects(task).toList.map(ProjectAndTask(proj, _)) :: p.children.flatMap(d => getTaskTree(d, task))
  }
}

case class ProjectAndTask(project: Project, task: Task) {

  private var lastRunTimeMs_ = 0L
  private var lastError_ : Option[TaskError] = None
  private var completed_ = false
  private var finishingTime_ = 0L
  var roundNo = 0

  def runTimeMs = lastRunTimeMs_
  def lastError = lastError_
  def completed = completed_
  def finishingTime = finishingTime_

  val immediateDependencies: Set[ProjectAndTask] = {
    project.dependencies.childProjectTasks(task)
  }

  def exec(acc: Map[Task, List[AnyRef]], parameters: Map[String, String] = Map()) = {
    ProjectAndTask.addTask(this)
    Log.debug("Executing " + this)
    val sw = new Stopwatch()
    val taskResult = try {
      task.exec(project, acc.getOrElse(task, Nil), parameters) match {
        case res@Left(err) =>
          lastError_ = Some(TaskError(err.reason, None))
          res
        case res => {
          completed_ = true
          res
        }
      }
    } catch {
      case e =>
        Log.info("Error occured when executing " + this)
        lastError_ = Some(TaskError("Internal Exception", Some(e)))
        e.printStackTrace
        Left(TaskFailed(this, e.getMessage))
    }
    val totalTime = sw.ms()
    lastRunTimeMs_ = totalTime
    finishingTime_ = System.nanoTime
    if (totalTime > 100 && Maker.verboseTestOutput && ! project.suppressTaskOutput)
      Log.info("%s completed in %dms".format(this, totalTime))
    taskResult
  }

  override def toString = "Task[" + project.name + ":" + task + "]"

  def runStats = toString + ", took " + runTimeMs + "ms"

  def allStats = "%s, status %s".format(
    runStats, lastError.map(_.toString).getOrElse("OK"))

  def getTaskTree: List[(ProjectAndTask, List[ProjectAndTask])] = ProjectAndTask.getTaskTree(project, task)
}
