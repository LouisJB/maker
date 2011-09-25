package maker.project

import java.io.File
import scala.io.Source

object SignatureFile{
  private val SigDirRegexp = """\.(.*)\.sig""".r
  def sigDir(scalaFile : File) = new File(scalaFile.getParentFile, "." + scalaFile.getName.replace(".scala", "") + ".sig")
  def forSourceFile(scalaFile : File) = SignatureFile(new File(sigDir(scalaFile), "sig"))
}

case class SignatureFile(file : File){
  import SignatureFile._
  file.getParentFile.mkdir

  def contents = Source.fromFile(file).mkString

  val scalaFile = {
    val dir = file.getParentFile
    val srcDir = dir.getParentFile
    dir.getName match {
      case SigDirRegexp(basename) => new File(srcDir, basename + ".scala")
    }
  }
}
