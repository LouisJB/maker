package maker.project

import maker.utils.FileUtils._
import java.io.File
import java.io.BufferedWriter
import java.io.BufferedReader

object Dependencies {
  val defaultFile = new File(".maker/dependencies")

  def apply(file: File = defaultFile): Dependencies = {
    var deps = Map[File, Set[File]]()
    if (file.exists) {
      withFileReader(file) {
        in: BufferedReader =>
          var line = in.readLine
          while (line != null) {
            val classFile :: sourceFileString :: Nil = line.split(":").toList
            val sourceFiles = sourceFileString.split(",").toSet.map {
              s: String => new File(s)
            }
            deps += new File(classFile) -> sourceFiles
            line = in.readLine
          }
      }
    }
    Dependencies(deps)
  }
}

case class Dependencies(private var deps: Map[File, Set[File]]) {

  import Dependencies._

  def persist(file: File = defaultFile) {
    withFileWriter(file) {
      out: BufferedWriter =>
        deps.foreach {
          case (classFile, sourceFiles) =>
            out.write(classFile.getPath)
            out.write(":")
            out.write(sourceFiles.map(_.getPath).mkString("", ",", "\n"))
        }
    }
  }

  def +=(classFile: File, sourceFiles: Set[File]) {
    deps = deps.updated(classFile, sourceFiles)
  }
}

