package maker.os

import java.lang.ProcessBuilder
import java.io.{File, OutputStream, InputStreamReader, BufferedReader, PrintWriter}
import maker.utils.{TeeToFileOutputStream, Log}


case class Command(os : OutputStream, args : String*) {
  def exec(async : Boolean = false) : (Int, String) = {
    Log.debug("Executing cmd (async = " + async + ") - " + toString)
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    val proc = procBuilder.start
    val (procResult, msg) = if (!async) {
      var ps : PrintWriter = null
      val buf = new StringBuffer()
      try {
        ps = new PrintWriter(os)
        val isr = new InputStreamReader(proc.getInputStream)
        val br = new BufferedReader(isr)
        var line : String =null;
        line = br.readLine()
        while (line != null) {
          ps.println(line)
          line = br.readLine()
          buf.append(line)
        }
      }
      finally {
        ps.flush()
        ps.close
      }
      (proc.waitFor, buf.toString)
    }
    else (0, "")
    (procResult, msg)
  }

  override def toString = "Command: " + args.mkString(" ")
}

object Command{
  def apply(args : String*) : Command = Command(System.out, args : _*)
  def apply(file : File, args : String*) : Command = Command(TeeToFileOutputStream(file), args : _*)
}
