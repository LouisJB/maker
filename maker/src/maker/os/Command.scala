package maker.os

import maker.utils.{TeeToFileOutputStream, Log}
import java.lang.ProcessBuilder
import java.io.{File, OutputStream, InputStreamReader, BufferedReader, PrintWriter}
import actors.Future
import actors.Futures._


case class Command(os : OutputStream, closeStream : Boolean, args : String*) {

  def startProc() : Process = {
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    procBuilder.start
  }

  def execProc() : (Process, Future[(Int,  String)]) = {
    val proc = startProc()
    (proc, future {
      waitFor(os, closeStream, proc)
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

  def waitFor(os : OutputStream, closeStream : Boolean, proc : Process) = {
    val buf = new StringBuffer()
    var ps : PrintWriter = null
    try {
      val br = new BufferedReader(new InputStreamReader(proc.getInputStream))
      ps = new PrintWriter(os, true)
      var line : String = null
      line = br.readLine()
      while (line != null) {
        ps.println(line)
        line = br.readLine()
        buf.append(line)
      }
    }
    finally {
      if (ps != null) {
        ps.flush
        if (closeStream) ps.close()
      }
    }
    (proc.waitFor, buf.toString)
  }

  override def toString = "Command: " + args.mkString(" ")
}

object Command{
  def apply(os : OutputStream, args : String*) : Command = Command(os, false, args : _*)
  def apply(args : String*) : Command = Command(System.out, false, args : _*)
  def apply(file : File, args : String*) : Command = Command(TeeToFileOutputStream(file), true, args : _*)
}
