package maker.task

import maker.utils.Log
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import maker.project.Project
import maker.os.Environment
import maker.os.Command

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

object Compile{
  def writeCompileInstructionsFile(compileInstructionFile: File, classpath : String, outputDir : File, srcFiles : List[File]){
    val fstream = new FileWriter(compileInstructionFile)
    val out = new BufferedWriter(fstream)

    out.write("-cp " + classpath + "\n")
    out.write("-d " + outputDir.getAbsolutePath + "\n")
    srcFiles.foreach{f : File => out.write(f.getAbsolutePath + "\n")}
    out.flush
    out.close()
  }
}

case class Compile(project : Project, dependencies : List[Task] = Nil) extends Task{
  import Environment._
  import project._
  import Compile._
  val lock = new Object

  def dependsOn(tasks : Task*) = copy(dependencies = (dependencies ::: tasks.toList).distinct)

  def compileRequired = {
    if (srcFiles.isEmpty)
      false
    else if (classFiles.isEmpty)
      true
    else 
      srcFiles.map(_.lastModified).max > classFiles.map(_.lastModified).min
  }
  def compileText = "-cp " + classpath + "\n" + "-d " + outputDir.getAbsolutePath + "\n" + srcFiles.mkString("\n") + "\n"


  protected def execSelf: (Int, String) = {
    if (!outputDir.exists)
      outputDir.mkdirs
    if (compileRequired){
      Log.info("Compiling")
      val compileInstructionFile = new File(root, "compile")
      writeCompileInstructionsFile(compileInstructionFile, classpath, outputDir, srcFiles)
      compileFromInstructionFile(compileInstructionFile)
    } else {
      Log.info("Already Compiling")
      (0, "Already compiled")
    }
  }



  def compileFromInstructionFile(compileInstructionFile : File) : (Int, String) = {
    Command(fsc, "@" + compileInstructionFile.getAbsolutePath).exec
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
    Log.info("cleaning")
    classFiles.foreach(_.delete)
    outputJar.delete
    (0, "")
  }
}

