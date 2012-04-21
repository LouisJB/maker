package maker.os

import java.lang.ProcessBuilder
import java.io.InputStreamReader
import java.io.BufferedReader
import maker.utils.Log

case class Command(args : String*) {
  def redirectOutputRunnable(proc : Process, handleLine : String ⇒ Unit) = new Runnable(){
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
  def exec() : (Int, String) = {
    Log.info("Executing cmd (async = " + false + ") - " + toString)
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
  override def toString = "Command: " + args.mkString(" ")
}
