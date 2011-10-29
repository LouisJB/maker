package maker.task

case class TaskFailed(task : Task[_], reason : String)
trait Task[T]{
  def lock : Object
  def exec : Either[TaskFailed, T] = {
    dependentTasks.foreach(
      _.exec match {
        case Left(TaskFailed(task, reason)) => return Left(TaskFailed(task, reason))
        case _ =>
      }
    )

    lock.synchronized{
      try {
        execSelf
      } catch {
        case ex =>
          Left(TaskFailed(this, ex.getMessage))
          ex.printStackTrace()
          throw ex
      }
    }
  }
  protected def execSelf : Either[TaskFailed, T]
  def dependentTasks : Seq[Task[_]]
  def dependsOn(tasks : Task[_]*) : Task[T]
}




