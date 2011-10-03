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

  def persist(){
    withFileWriter(file) {
      out: BufferedWriter =>
        sigs.foreach {
          case (sourceFile, hash) =>
            out.write(sourceFile.getPath)
            out.write(":")
            out.write(hash.toString)
        }
    }
  }
}
