package maker.task

import maker.project.Project
import maker.utils.Log


//case class Clean(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[Unit]{
//  import project._
//  val lock = new Object
//  def dependsOn(tasks : Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)
//  def execSelf = {
//    Log.info("cleaning " + project)
//    classFiles.foreach(_.delete)
//    testClassFiles.foreach(_.delete)
//    outputJar.delete
//    Right(Unit)
//  }
//}
