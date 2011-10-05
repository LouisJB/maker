package plugin

import java.io.File
import maker.utils.FileUtils._
import java.io.BufferedWriter

object SourceFileSignatures{
  def apply(file : File) : SourceFileSignatures = {
    val sigs : Map[File, Long] = extractMapFromFile(
      file,
      (line : String) => {
        val sourceFile :: hash :: Nil = line.split(":").toList
        (new File(sourceFile), hash.toLong)
      }
    )
    new SourceFileSignatures(sigs, file)
  }
}

case class SourceFileSignatures(private var sigs : Map[File, Long], file : File){

  def += (file : File, hash : Long) = {
    val updated = sigs.get(file) match {
      case Some(currentHash) if hash == currentHash => false
      case _ => true
    }
    sigs = sigs + (file -> hash)
    updated
  }

  def persist(){
    writeMapToFile(file, sigs, {(f : File, hash : Long) => f.getPath + ":" + hash})
  }

  def timestamp: Long = if (file.exists)
    file.lastModified()
  else
    0
}
