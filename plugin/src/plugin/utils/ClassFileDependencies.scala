package plugin.utils

import maker.utils.FileUtils._
import java.io.File
import java.io.BufferedReader
import scala.collection.mutable.Map


case class ClassFileDependencies(persistFile: File, var deps: Map[File, Set[File]] = Map[File, Set[File]]()) {

  if (deps.isEmpty && persistFile.exists) {
    withFileReader(persistFile) {
      in: BufferedReader =>
        var line = in.readLine
        while (line != null) {
          line.split(":").toList match {
            case sourceFile :: Nil => deps = deps.updated(new File(sourceFile), Set[File]())
            case sourceFile :: itsDependenciesAsString :: Nil =>
              val itsDependencies: Set[File] = itsDependenciesAsString.toString.split(",").toSet.map {
                s: String => new File(s)
              }
              deps = deps.updated(new File(sourceFile), itsDependencies)
            case _ => throw new Exception("Unexpected line in deps file " + persistFile)
          }
          line = in.readLine
        }
    }
  }

  def persist() {
    writeMapToFile(
      persistFile, deps,
      (f: File, s: Set[File]) => f.getPath + ":" + s.map(_.getPath).mkString("", ",", "")
    )
  }

  def +=(classFile: File, sourceFiles: Set[File]) {
    deps = deps.updated(classFile, sourceFiles)
  }

  def dependentFiles(files: scala.collection.Set[File]): Set[File] = Set[File]() ++ deps.filter {
    case (source, dependencies) => files.exists(dependencies.contains)
  }.keySet

  def asMap = Map[File, Set[File]]() ++ deps
}
