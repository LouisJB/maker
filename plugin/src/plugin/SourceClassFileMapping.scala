package plugin

import scala.collection.mutable.{Set => MSet, Map => MMap}
import java.io.{BufferedReader, BufferedWriter, File}
import maker.utils.FileUtils
import maker.utils.Log

case class SourceClassFileMapping(persistFile : File, private var mapping : Map[File, Set[File]] = Map[File, Set[File]]()){
  if (mapping.isEmpty && persistFile.exists){
    FileUtils.withFileReader(persistFile){
      in : BufferedReader => {
        var line = in.readLine
        while (line != null){
          val sourceFile :: classFiles = line.split(":").toList.map(new File(_))
          ++= (sourceFile, classFiles)
          line = in.readLine
        }
      }
    }
  }
  def += (sourceFile : File, classFile : File){
    mapping = mapping.updated(sourceFile, mapping.getOrElse(sourceFile, Set[File]()) + classFile)
  }
  def ++=(sourceFile : File, classFiles : Iterable[File]){
    mapping = mapping.updated(sourceFile, mapping.getOrElse(sourceFile, Set[File]()) ++ classFiles)
  }
  def persist(){
    FileUtils.withFileWriter(persistFile){
      writer : BufferedWriter =>
        mapping.foreach{
          case (sourceFile, classFiles) =>
            writer.write((sourceFile :: classFiles.toList).map(_.getPath).mkString(":") + "\n")
        }
    }
  }
  def persistedMapping = SourceClassFileMapping(persistFile).mapping
  def map = mapping
}


