package maker.task

import maker.utils.Log
import maker.utils.FileUtils._
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import maker.project.Project
import maker.project.SignatureFile
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
    val out = outputStream(compileInstructionFile)

    val pluginJar = "out/artifacts/plugin_jar/plugin.jar"
    out.write("-unchecked\n")
    out.write("-Xplugin:" + pluginJar + "\n")
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
    if (srcFiles.isEmpty){
      Log.warn("No source files found")
      false
    }
    else if (classFiles.isEmpty)
      true
    else 
      srcFiles.map(_.lastModified).max > classFiles.map(_.lastModified).min
  }


  protected def execSelf: (Int, String) = {
    if (!outputDir.exists)
      outputDir.mkdirs
    if (compileRequired){
      Log.info("Compiling " + project)
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
              val out = outputStream(sigFile.file)
              out.write(sig.reverse.mkString("\n"))
              out.close
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

