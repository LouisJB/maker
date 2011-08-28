package maker

import java.io.File


case class Project(root : File, srcDirs : List[File], jars : List[File], outputDir : File){
  def srcFiles = {
    def rec(file : File) : List[File] = {
      if (file.isDirectory)
        file.listFiles.toList.flatMap(rec)
      else if (file.getName.endsWith(".scala"))
        List(file)
      else
        Nil
    }
    srcDirs.flatMap(rec)
  }
}

object Project extends App{
  val root = new File(System.getenv("HOME") + "/github/maker")
  val proj = new Project(root, List(new File(root, "src")), Nil, new File(root, "/target/scala_2.9.0-1/classes"))
  proj.srcFiles.foreach(println)
}



