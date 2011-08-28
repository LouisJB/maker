package maker

import maker.utils.Log
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.lang.ProcessBuilder
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream


case class Project(root : File, srcDirs : List[File], jars : List[File], outputDir : File){

  private def findFiles(pred : File => Boolean, dirs : File*) : List[File] = {
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

  def srcFiles = findFiles({f : File => f.getName.endsWith(".scala")}, srcDirs : _*)
  def classFiles = findFiles({f : File => f.getName.endsWith(".class")}, outputDir)
  def classpath = (outputDir :: jars).map(_.getAbsolutePath).mkString(":")
  def compileText = "-cp " + classpath + "\n" + "-d " + outputDir.getAbsolutePath + "\n" + srcFiles.mkString("\n") + "\n"
  def clean {
    Log.info("cleaning")
    classFiles.foreach(_.delete)
  }
  def compile : String = {
    Log.info("Compiling")
    val compileInstructionFile = new File(root, "compile")
    val fstream = new FileWriter(compileInstructionFile)
    val out = new BufferedWriter(fstream)
    out.write(compileText)
    out.flush
    out.close()
    val procBuilder = new ProcessBuilder("fsc", "@" + compileInstructionFile.getAbsolutePath)
    procBuilder.redirectErrorStream
    val proc = procBuilder.start
    val procResult = proc.waitFor
    if (procResult != 0){
      Project.getStringFromInputStream(proc.getInputStream)
    } else {
      ""
    }
  }
}

object Project extends App{
  val root = new File(System.getenv("HOME") + "/github/maker")
  val proj = new Project(root, List(new File(root, "src")), Nil, new File(root, "/target/scala_2.9.0-1/classes"))
  proj.srcFiles.foreach(println)
  proj.compile

  def getStringFromInputStream(s : InputStream) : String = {
    val bis = new BufferedInputStream(s)
    val buf = new ByteArrayOutputStream()
    var result = bis.read()
    while(result != -1) {
      val b = result.asInstanceOf[Byte]
      buf.write(b)
      result = bis.read()
    }        
    buf.toString()
  }

}



