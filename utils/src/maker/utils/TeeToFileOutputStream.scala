package maker.utils

import java.io.{OutputStream, File}


case class TeeToFileOutputStream(file : File, os : OutputStream = Console.out) extends OutputStream {
  import java.io.FileOutputStream
  import java.io.PrintStream
  import org.apache.commons.io.output.TeeOutputStream

  protected def makeTeeStream = {
    new PrintStream(
      new TeeOutputStream(
        os,
        new PrintStream(new FileOutputStream(file))
      )
    )
  }

  var tee = makeTeeStream
  def write(b : Int){
    tee.write(b)
  }
}
