package maker.task

import maker.utils.FileUtils._
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import maker.project.Project
import maker.os.Environment
import maker.os.Command
import maker.utils.{FileUtils, Log}

trait Task[T]{
  def lock : Object
  def exec : (Int, Option[T]) = {
    dependentTasks.foreach{
      dep =>
        val (depResult, _) = dep.exec
        if (depResult != 0)
          return (1, None)
    }
    lock.synchronized{
      val (returnCode, result) = execSelf
      (returnCode, Some(result))
    }
  }
  protected def execSelf : (Int, T)
  def dependentTasks : Seq[Task[_]]
  def dependsOn(tasks : Task[_]*) : Task[T]
}


case class UpdateSignatures(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[Set[File]]{
  import Environment._
  import project._

  val CompiledFromRegex = """Compiled from \"(\w+).*""".r
  val lock = new Object
  def dependsOn(tasks : Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)
  def execSelf : (Int, Set[File]) = {
    val classFilesToProcess = project.classFiles.filter(_.lastModified() > project.signatures.timestamp)
    val shortClassNames =
      classFilesToProcess.map {
        cf =>
          cf.getPath.substring(outputDir.getPath.length + 1).replace("/", ".").replace(".class", "")
      }.toList

    var sourceFileSigs = Map[File, List[String]]()
    var currentSourceFile : Option[File] = None
    shortClassNames.grouped(40).foreach {
      group =>
        val args = List(javap, "-classpath", outputDir.getPath) ::: group
        val cmd = Command(args: _*)
        cmd.exec match {
          case (0, sigs) => {
            sigs.split("\n").foreach {
              case CompiledFromRegex(scalaFile) =>
                currentSourceFile = Some(new File(scalaFile))
              case line =>
                currentSourceFile match {
                  case Some(file) =>
                    sourceFileSigs = sourceFileSigs.updated(file, (line :: sourceFileSigs.getOrElse(file, Nil)))
                  case None =>
                    throw new Exception("No source file in line " + line)
                }
            }
          }

          case (err, _) =>
            throw new Exception("Error " + err + " when executing " + cmd)
        }
    }
    val sourceFilesWithChangedSignature = sourceFileSigs.keySet.filter{
      sourceFile =>
        val hash = sourceFileSigs(sourceFile).sortWith(_<_).hashCode
        project.signatures += (sourceFile, hash)
    }
    (0, sourceFilesWithChangedSignature)
  }
}

case class Package(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[String]{
  import Environment._
  import project._
  val lock = new Object
  def dependsOn(tasks : Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)

  def execSelf : (Int, String) = {
    if (!packageDir.exists)
      packageDir.mkdirs

    val cmd = Command(jar, "cf", outputJar.getAbsolutePath, "-C", new File(outputDir.getAbsolutePath).getParentFile.toString, outputDir.getName)
    cmd.exec
  }
}

case class Clean(project : Project, dependentTasks : List[Task[_]] = Nil) extends Task[String]{
  import project._
  val lock = new Object
  def dependsOn(tasks : Task[_]*) = copy(dependentTasks = (dependentTasks ::: tasks.toList).distinct)
  def execSelf =  {
    Log.info("cleaning " + project)
    classFiles.foreach(_.delete)
    outputJar.delete
    (0, "")
  }
}

