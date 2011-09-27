package maker.task

import maker.utils.FileUtils._
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import maker.project.Project
import maker.project.SignatureFile
import maker.os.Environment
import maker.os.Command
import maker.utils.{FileUtils, Log}

trait Task{
  def lock : Object
  def exec : (Int, String) = {
    dependencies.foreach{
      dep =>
        val (depResult, depOutput) = dep.exec
        if (depResult != 0)
          return (1, "")
    }
    lock.synchronized{
      execSelf
    }
  }
  protected def execSelf : (Int, String)
  def dependencies : Seq[Task]
  def dependsOn(tasks : Task*) : Task
}


case class WriteSignatures(project : Project, dependencies : List[Task] = Nil) extends Task{
  import Environment._
  import project._

  val lock = new Object
  def dependsOn(tasks : Task*) = copy(dependencies = (dependencies ::: tasks.toList).distinct)
  def execSelf : (Int, String) = {
    traverseDirectories(outputDir, {
        dir => 
          val classFiles = dir.listFiles.filter(_.getName.endsWith(".class"))
          val shortClassNames = 
            classFiles.map {
              cf =>
                cf.getPath.substring(outputDir.getPath.length + 1).replace("/", ".").replace(".class", "")
            }.toList
          val CompiledFromRegex = """Compiled from \"(\w+).*""".r
          var sigBySourceFile = Map[String, List[String]]()
          var currentSourceFile : Option[String] = None
          shortClassNames.grouped(40).foreach{
            group => 
            val args = List(javap, "-classpath", outputDir.getPath) ::: group
            val cmd = Command(args : _*)
            cmd.exec match {
              case (0, sigs) => {
                sigs.split("\n").foreach {
                      case CompiledFromRegex(scalaFile) => 
                        currentSourceFile = Some(scalaFile)
                      case line =>
                        currentSourceFile match {
                          case Some(file) => 
                            sigBySourceFile = sigBySourceFile.updated(file, line :: sigBySourceFile.getOrElse(file, Nil))
                          case None =>
                            throw new Exception("No source file in line " + line)
                      }
                    }
                }
                    
              case (err, _) => 
                throw new Exception("Error " + err + " when executing " + cmd)
            }
          }
          sigBySourceFile.foreach{
            case (srcFileName, sig) =>
              val sigFile = SignatureFile.forSourceFile(new File(dir, srcFileName + ".scala"))
//              writeToFile(sigFile, sig.reverse.mkString("\n"))
          }
        }
      )
    (0, "")
  }
}

case class Package(project : Project, dependencies : List[Task] = Nil) extends Task{
  import Environment._
  import project._
  val lock = new Object
  def dependsOn(tasks : Task*) = copy(dependencies = (dependencies ::: tasks.toList).distinct)

  def execSelf : (Int, String) = {
    if (!packageDir.exists)
      packageDir.mkdirs

    val cmd = Command(jar, "cf", outputJar.getAbsolutePath, "-C", new File(outputDir.getAbsolutePath).getParentFile.toString, outputDir.getName)
    cmd.exec
  }
}

case class Clean(project : Project, dependencies : List[Task] = Nil) extends Task{
  import project._
  val lock = new Object
  def dependsOn(tasks : Task*) = copy(dependencies = (dependencies ::: tasks.toList).distinct)
  def execSelf =  {
    Log.info("cleaning " + project)
    classFiles.foreach(_.delete)
    outputJar.delete
    (0, "")
  }
}

