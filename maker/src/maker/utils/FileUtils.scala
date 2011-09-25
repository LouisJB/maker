package maker.utils

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter

object FileUtils{

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

  def findFilesWithExtension(ext : String, dirs : File*) = {
    findFiles(
      {f : File => f.getName.endsWith("." + ext)},
      dirs : _*
    )
  }

  def traverseDirectories(root : File, fn : File => Unit){
    fn(root)
    root.listFiles.filter(_.isDirectory).foreach(traverseDirectories(_, fn))
  }

  def findJars(dirs : File*) = findFilesWithExtension("jar", dirs : _*)
  def findClasses(dirs : File*) = findFilesWithExtension("class", dirs : _*)
  def findSourceFiles(dirs : File*) = findFilesWithExtension("scala", dirs : _*)

  def outputStream(file : File) = {
    val fstream = new FileWriter(file)
    new BufferedWriter(fstream)
  }

  def tempDir(name : String = "") = {
    val temp = File.createTempFile(name, java.lang.Long.toString(System.nanoTime))
    temp.delete
    temp.mkdirs
    temp
  }

  def recursiveDelete(file : File){
    if (file.isDirectory){
      file.listFiles.foreach(recursiveDelete)
      file.delete
    } else
      file.delete
  }

  def writeToFile(file : File, text : String){
    file.getParentFile.mkdirs
    val out = outputStream(file)
    out.write(text)
    out.close
  }
}
