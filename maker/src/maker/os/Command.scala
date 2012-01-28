package maker.os

import java.lang.ProcessBuilder
import java.io.InputStreamReader
import java.io.BufferedReader

case class Command(args : String*){
  def exec : (Int, String) = {
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    val proc = procBuilder.start
    val isr = new InputStreamReader(proc.getInputStream);
    val br = new BufferedReader(isr);
    val buf = new StringBuffer()
    var line : String =null;
    line = br.readLine()
    while ( line != null){
      System.out.println(line);
      line = br.readLine()
      buf.append(line)
    }
    val procResult = proc.waitFor
    (procResult, buf.toString)
  }

  override def toString = "Command: " + args.mkString(" ")
}
