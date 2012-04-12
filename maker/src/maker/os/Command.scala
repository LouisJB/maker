package maker.os

import maker.utils.{TeeToFileOutputStream, Log}
import java.lang.ProcessBuilder
import java.io.{File, OutputStream, InputStreamReader, BufferedReader, PrintWriter}
import actors.Future
import actors.Futures._


case class Command(os : OutputStream, closeStream : Boolean, pwd : Option[File], args : String*) {

  def startProc() : Process = {
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    pwd.map(procBuilder.directory(_))
    procBuilder.start
  }

  def execProc() : (Process, Future[(Int,  String)]) = {
    Log.debug("Executing cmd - " + toString)
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
      if (closeStream) ps.println("Executing command:\n%s\n".format(asString)) 
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

  def asString = args.mkString(" ")
  override def toString = "Command: " + asString
}

object Command{
  def apply(os : OutputStream, args : String*) : Command = Command(os, false, None, args : _*)
  def apply(args : String*) : Command = Command(System.out, false, None, args : _*)
  def apply(file : File, args : String*) : Command = Command(TeeToFileOutputStream(file), true, None, args : _*)
  def apply(pwd : Option[File], args : String*) : Command = Command(System.out, false, pwd, args : _*)
}
