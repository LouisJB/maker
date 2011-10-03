package plugin

import maker.utils.FileUtils._
import java.io.File
import java.io.BufferedWriter
import java.io.BufferedReader

object Dependencies {

  def apply(file: File): Dependencies = {
    val deps : Map[File, Set[File]] = extractMapFromFile(
      file,
      (line : String) => {
        val sourceFile :: itsDependenciesAsString :: Nil = line.split(":").toList
        val itsDependencies : Set[File] = itsDependenciesAsString.split(",").toSet.map {s : String => new File(s)}
        (new File(sourceFile), itsDependencies)
      }
    )
    new Dependencies(deps, file)
  }
}

class Dependencies(private var deps: Map[File, Set[File]], file : File) {

  import Dependencies._

  def persist() {
    writeMapToFile(
      file, deps,
      (f : File, s : Set[File]) => f.getPath + ":" + s.map(_.getPath).mkString("", ",", "\n")
    )
  }

  def +=(classFile: File, sourceFiles: Set[File]) {
    deps = deps.updated(classFile, sourceFiles)
  }

  def dependentFiles(files : Set[File]) = deps.filter {
    case (source, dependencies) => files.exists(dependencies.contains)
  }.keys
}

