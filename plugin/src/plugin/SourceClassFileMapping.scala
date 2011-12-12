package plugin

import scala.collection.mutable.{Set => MSet, Map => MMap}
import java.io.{BufferedReader, BufferedWriter, File}
import maker.utils.FileUtils
import maker.utils.Log

case class SourceClassFileMapping(persistFile : File, private val mapping : MMap[File, Set[File]] = MMap[File, Set[File]]()){
  def += (sourceFile : File, classFile : File){
    mapping.update(sourceFile, mapping.getOrElse(sourceFile, Set[File]()) + classFile)
  }
  def ++=(sourceFile : File, classFiles : Iterable[File]){
    mapping.update(sourceFile, mapping.getOrElse(sourceFile, Set[File]()) ++ classFiles)
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
}

object SourceClassFileMapping{
  def build(file : File) : SourceClassFileMapping = {
    val mapping = SourceClassFileMapping(file)
    var line : String = null
    FileUtils.withFileReader(file){
      in : BufferedReader => {
        line = in.readLine
        while (line != null){
          val sourceFile :: classFiles = line.split(":").toList.map(new File(_))
          mapping ++= (sourceFile, classFiles)
          line = in.readLine
        }
      }
    }
    mapping
  }
}

