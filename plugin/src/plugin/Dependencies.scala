package plugin

import maker.utils.FileUtils._
import java.io.File
import java.io.BufferedWriter
import java.io.BufferedReader
import collection.Iterable

object Dependencies {

  def apply(file: File): Dependencies = {
    val deps : Map[File, Set[File]] = extractMapFromFile(
      file,
      (line : String) => {
        line.split(":").toList match {
          case sourceFile :: Nil => (new File(sourceFile), Set[File]())
          case sourceFile :: itsDependenciesAsString :: Nil =>
            val itsDependencies : Set[File] = itsDependenciesAsString.toString.split(",").toSet.map {s : String => new File(s)}
            (new File(sourceFile), itsDependencies)
          case _ => throw new Exception("Unexpected line in deps file " + file)
        }
      }
    )
    new Dependencies(deps, file)
  }
}

case class Dependencies(private var deps: Map[File, Set[File]], file : File) {

  import Dependencies._

  def persist() {
    writeMapToFile(
      file, deps,
      (f : File, s : Set[File]) => f.getPath + ":" + s.map(_.getPath).mkString("", ",", "")
    )
  }

  def +=(classFile: File, sourceFiles: Set[File]) {
    deps = deps.updated(classFile, sourceFiles)
  }

  def dependentFiles(files : scala.collection.Set[File]): Set[File] = deps.filter {
    case (source, dependencies) => files.exists(dependencies.contains)
  }.keySet
  def asMap = Map[File, Set[File]]() ++ deps
}

