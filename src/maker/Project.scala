package maker

import maker.utils.Log
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.lang.ProcessBuilder
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

object Project{
  var projects : List[Project] = Nil
  var currentProject : Option[Project] = None
  def current = currentProject match {
    case None => {
      currentProject = projects.headOption
      currentProject.get
    }
    case Some(proj) => proj
  }

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

  def compileFromInstructionFile(compileInstructionFile : File) : String = {
    val procBuilder = new ProcessBuilder("/usr/local/scala/bin/fsc", "@" + compileInstructionFile.getAbsolutePath)
    //procBuilder.redirectErrorStream
    val proc = procBuilder.start
    val procResult = proc.waitFor
    Project.getStringFromInputStream(proc.getInputStream)
  }
}

case class Project(root : File, srcDirs : List[File], jars : List[File], outputDir : File){
  import Project._


  def srcFiles = findFiles({f : File => f.getName.endsWith(".scala")}, srcDirs : _*)
  def classFiles = findFiles({f : File => f.getName.endsWith(".class")}, outputDir)
  def classpath = (outputDir :: jars).map(_.getAbsolutePath).mkString(":")
  def compileText = "-cp " + classpath + "\n" + "-d " + outputDir.getAbsolutePath + "\n" + srcFiles.mkString("\n") + "\n"
  def clean {
    Log.info("cleaning")
    classFiles.foreach(_.delete)
  }

  def compile: String = {
    if (!outputDir.exists)
      outputDir.mkdirs
    Log.info("Compiling")
    val compileInstructionFile = new File(root, "compile")
    writeCompileInstructionsFile(compileInstructionFile, classpath, outputDir, srcFiles)
    compileFromInstructionFile(compileInstructionFile)

  }
}

