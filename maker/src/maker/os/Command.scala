package maker.os

import java.lang.ProcessBuilder
import java.io.{File, OutputStream, InputStreamReader, BufferedReader, PrintWriter}
import maker.utils.{TeeToFileOutputStream, Log}
import actors.Future
import actors.Futures._


case class Command(os : OutputStream, args : String*) {

  private def startProc() : Process = {
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    procBuilder.start
  }

  def execProc() : (Process, Future[(Int,  String)]) = {
    val proc = startProc()
    val isr = new InputStreamReader(proc.getInputStream)
    val br = new BufferedReader(isr)
    (proc, future {
      val buf = new StringBuffer()
      val ps = new PrintWriter(os, true)
      var line : String = null
      line = br.readLine()
      while (line != null) {
        ps.println(line)
        line = br.readLine()
        buf.append(line)
      }
      (proc.waitFor, buf.toString)
    })
  }

  def exec(async : Boolean = false) : (Int, String) = {
    Log.debug("Executing cmd (async = " + async + ") - " + toString)
    val f = execProc()
    if (!async) {
      if (!f._2.isSet) awaitAll(Long.MaxValue/2, f._2)
      f._2()
    }
    else (0, "")
  }

  // keeping this around just in case it's useful, should be replaced with the equivalent above so will remove shortly anyway
  @deprecated
  def execInline(async : Boolean = false) : (Int, String) = {
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
