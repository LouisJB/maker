package maker.project

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
import maker.task.Clean
import maker.task.Compile
import maker.task.Package

object Project{

  private def java_home = ("/usr/local/jdk" :: List("JAVA_HOME", "MAKER_JAVA_HOME").flatMap{e : String => Option(System.getenv(e))}).filter(new File(_).exists).headOption.getOrElse(throw new Exception("Can't find scala home"))

  private def jar : String = java_home + "/bin/jar"



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

}

case class Project(
  name : String,
  root : File,
  srcDirs : List[File],
  jars : List[File],
  outputDir : File,
  packageDir : File,
  dependencies : List[Project] = Nil
){
  import Project._

  def dependsOn(projects : Project *) = copy(dependencies = dependencies ::: projects.toList)

  def srcFiles = findFiles({f : File => f.getName.endsWith(".scala")}, srcDirs : _*)
  def classFiles = findFiles({f : File => f.getName.endsWith(".class")}, outputDir)
  def classpath = (outputDir :: jars).map(_.getAbsolutePath).mkString(":")
  def outputJar = new File(packageDir.getAbsolutePath, name + ".jar")

  private val cleanTask = Clean(this)
  private val compileTask = Compile(this)
  private val packageTask = Package(this) dependsOn(compileTask)
  
  def clean: (Int, String) = cleanTask.exec
  def compile: (Int, String) = compileTask.exec
  def pack : (Int, String) = packageTask.exec
}

