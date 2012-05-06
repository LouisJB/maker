package maker.utils.os

import maker.utils.{TeeToFileOutputStream, Log}
import java.lang.ProcessBuilder
import java.io.{File, OutputStream, InputStreamReader, BufferedReader, PrintWriter}
import actors.Future
import actors.Futures._
import org.apache.commons.io.output.NullOutputStream


case class Command(outputStream : OutputStream, closeStream : Boolean, pwd : Option[File], args : String*) {

  import Command._

  def withNullOutput = Command(new NullOutputStream, closeStream, pwd, args : _*)

  private def redirectOutputRunnable(proc : Process, handleLine : String ⇒ Unit) = new Runnable(){
    def run(){
      val br = new BufferedReader(new InputStreamReader(proc.getInputStream))
      var line : String =null;
      line = br.readLine()
      while (line != null) {
        handleLine(line)
        line = br.readLine()
      }
    }
  }

  private def startProc() : Process = {
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    pwd.foreach(procBuilder.directory(_))
    procBuilder.start
  }

  def execProc() : (Process, Future[(Int,  String)]) = {
    Log.debug("Executing cmd - " + toString)
    val proc = startProc()
    (proc, future {
      waitFor(outputStream, closeStream, proc)
    })
  }

  def exec() : (Int, String) = {
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    val proc = procBuilder.start
    val buf = new StringBuffer()
    redirectOutputRunnable(proc, {line : String ⇒ buf.append(line); System.out.println(line)}).run
    (proc.waitFor, buf.toString)
  }


  def execAsync : Process = {
    Log.debug("Executing cmd (async = " + true + ") - " + toString)
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    val proc = procBuilder.start
    val redirectOutput = redirectOutputRunnable(proc, {line : String ⇒ System.out.println(line)})
    new Thread(redirectOutput).start
    proc
  }
  def asString = args.mkString(" ")
  override def toString = "Command: " + asString
}

object Command{
  def apply(os : OutputStream, args : String*) : Command = Command(os, false, None, args : _*)
  def apply(args : String*) : Command = Command(System.out, false, None, args : _*)
  def apply(file : File, args : String*) : Command = Command(TeeToFileOutputStream(file), true, None, args : _*)
  def apply(pwd : Option[File], args : String*) : Command = Command(System.out, false, pwd, args : _*)

  private def waitFor(outputStream : OutputStream, closeStream : Boolean, proc : Process) = {
    val buf = new StringBuffer()
    var ps : PrintWriter = null
    try {
      val br = new BufferedReader(new InputStreamReader(proc.getInputStream))
      ps = new PrintWriter(outputStream, true)
      var line : String = null
      line = br.readLine()
      while (line != null) {
        ps.println(line)
        line = br.readLine()
        buf.append(line)
      }
    } finally {
      if (ps != null) {
        ps.flush
        if (closeStream) ps.close()
      }
    }
    (proc.waitFor, buf.toString)
  }
}

object ScalaCommand{
  def apply(java : String, classpath : String, klass : String, args : String*) : Command = {
    val allArgs : List[String] = List(
      java,
      "-Dscala.usejavacp=true",
      "-classpath",
      classpath,
      "scala.tools.nsc.MainGenericRunner",
      klass) ::: args.toList
    Command(allArgs :_*)
  }
}
