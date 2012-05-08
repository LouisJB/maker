package plugin.utils

import scala.collection.mutable.{Set => MSet, Map => MMap}
import java.io.{BufferedReader, BufferedWriter, File}
import maker.utils.FileUtils._

case class SourceClassFileMapping(persistFile: File, private var mapping: Map[File, Set[File]] = Map[File, Set[File]]()) {
  if (mapping.isEmpty && persistFile.exists) {
    withFileLineReader(persistFile) {
      line : String ⇒ 
        val sourceFile :: classFiles = line.split(":").toList.map(new File(_))
        ++=(sourceFile, classFiles)
    }
  }

  def +=(sourceFile: File, classFile: File) {
    mapping = mapping.updated(sourceFile, mapping.getOrElse(sourceFile, Set[File]()) + classFile)
  }

  def ++=(sourceFile: File, classFiles: Iterable[File]) {
    mapping = mapping.updated(sourceFile, mapping.getOrElse(sourceFile, Set[File]()) ++ classFiles)
  }

  def persist() {
    withFileWriter(persistFile) {
      writer : BufferedWriter =>
        mapping.foreach {
          case (sourceFile, classFiles) =>
            writer.println((sourceFile :: classFiles.toList).map(_.getPath).mkString(":"))
        }
    }
  }

  def persistedMapping = SourceClassFileMapping(persistFile).mapping

  def map = mapping

  def sourceFiles = mapping.keySet

  def classFilesFor(srcFiles: Set[File]): Set[File] = srcFiles.flatMap(mapping)
}

