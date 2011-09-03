package maker

import maker.utils.Log
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.lang.ProcessBuilder
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.System
import scala.collection.JavaConversions._

object Project{

  private def scala_home = ("/usr/local/scala" :: List("SCALA_HOME", "MAKER_SCALA_HOME").flatMap{e : String => Option(System.getenv(e))}).filter(new File(_).exists).headOption.getOrElse(throw new Exception("Can't find scala home"))
  private def java_home = ("/usr/local/jdk" :: List("JAVA_HOME", "MAKER_JAVA_HOME").flatMap{e : String => Option(System.getenv(e))}).filter(new File(_).exists).headOption.getOrElse(throw new Exception("Can't find scala home"))

  private def jar : String = java_home + "/bin/jar"
  private def fsc : String = scala_home + "/bin/fsc"



  def findFiles(pred : File => Boolean, dirs : File*) : List[File] = {
    def rec(file : File) : List[File] = {
      if (file.isDirectory)
        file.listFiles.toList.flatMap(rec)
      else if (pred(file))
        List(file)
      else
        Nil
    }
    dirs.toList.flatMap(rec)
  }

  def writeCompileInstructionsFile(compileInstructionFile: File, classpath : String, outputDir : File, srcFiles : List[File]){
    val fstream = new FileWriter(compileInstructionFile)
    val out = new BufferedWriter(fstream)

    out.write("-cp " + classpath + "\n")
    out.write("-d " + outputDir.getAbsolutePath + "\n")
    srcFiles.foreach{f : File => out.write(f.getAbsolutePath + "\n")}
    out.flush
    out.close()
  }


  def compileFromInstructionFile(compileInstructionFile : File) : (Int, String) = {
    Command(fsc, "@" + compileInstructionFile.getAbsolutePath).exec
  }
}

case class Project(name : String, root : File, srcDirs : List[File], jars : List[File], outputDir : File, packageDir : File){
  import Project._

  def compileRequired = {
    if (srcFiles.isEmpty)
      false
    else if (classFiles.isEmpty)
      true
    else 
      srcFiles.map(_.lastModified).max > classFiles.map(_.lastModified).min
  }

  def srcFiles = findFiles({f : File => f.getName.endsWith(".scala")}, srcDirs : _*)
  def classFiles = findFiles({f : File => f.getName.endsWith(".class")}, outputDir)
  def classpath = (outputDir :: jars).map(_.getAbsolutePath).mkString(":")
  def compileText = "-cp " + classpath + "\n" + "-d " + outputDir.getAbsolutePath + "\n" + srcFiles.mkString("\n") + "\n"
  def clean {
    Log.info("cleaning")
    classFiles.foreach(_.delete)
    outputJar.delete
  }

  def compile: (Int, String) = {
    if (!outputDir.exists)
      outputDir.mkdirs
    Log.info("Compiling")
    if (compileRequired){
      val compileInstructionFile = new File(root, "compile")
      writeCompileInstructionsFile(compileInstructionFile, classpath, outputDir, srcFiles)
      compileFromInstructionFile(compileInstructionFile)
    } else {
      (0, "Already compiled")
    }
  }

  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")

  def pack : Int = {
    if (!packageDir.exists)
      packageDir.mkdirs
    compile

    val cmd = Command(jar, "cf", outputJar.getAbsolutePath, "-C", new File(outputDir.getAbsolutePath).getParentFile.toString, outputDir.getName)
    val (result, output) = cmd.exec
    result
  }

}

