package maker.os

import java.io.File

object Environment{
  private def scala_home = ("/usr/local/scala" :: List("SCALA_HOME", "MAKER_SCALA_HOME").flatMap{e : String => Option(System.getenv(e))}).filter(new File(_).exists).headOption.getOrElse(throw new Exception("Can't find scala home"))
  private def java_home = ("/usr/local/jdk" :: List("JAVA_HOME", "MAKER_JAVA_HOME").flatMap{e : String => Option(System.getenv(e))}).filter(new File(_).exists).headOption.getOrElse(throw new Exception("Can't find scala home"))

  def jar : String = java_home + "/bin/jar"
  def fsc : String = scala_home + "/bin/fsc"



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


