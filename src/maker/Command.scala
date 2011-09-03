package maker

import java.io.BufferedWriter
import java.lang.ProcessBuilder
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

case class Command(args : String*){
  override def toString = "Command: " + args.mkString(" ")

  def exec : (Int, String) = {
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream
    val proc = procBuilder.start
    val procResult = proc.waitFor
    val output = getStringFromInputStream(proc.getInputStream)
    (procResult, output)
  }

  private def getStringFromInputStream(s : InputStream) : String = {
    val bis = new BufferedInputStream(s)
    val buf = new ByteArrayOutputStream()
    var result = bis.read()
    while(result != -1) {
      val b = result.asInstanceOf[Byte]
      buf.write(b)
      result = bis.read()
    }        
    buf.toString()
  }
}

