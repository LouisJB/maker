package maker.utils.os

import maker.utils.Log
import java.lang.ProcessBuilder
import java.io.{File, InputStreamReader, BufferedReader, PrintWriter}
import actors.Future
import actors.Futures._
import java.io.FileWriter
import scalaz.Scalaz._


case class CommandOutputHandler(writer : Option[PrintWriter] = Some(new PrintWriter(System.out)),
                                buffer : Option[StringBuffer] = None,
                                closeWriter : Boolean = false) {
  def withSavedOutput = copy(buffer = Some(new StringBuffer()))
  def savedOutput = buffer.fold(_.toString, "")
  def processLine(line : String){
    writer.foreach{
      w ⇒
        w.println(line)
        w.flush
    }
    buffer.foreach(_.append(line))
  }
  def close {
    writer.foreach{
      w =>
        w.flush
        if (closeWriter)
          w.close
    }
  }

  def redirectOutputRunnable(proc : Process) = new Runnable(){
    def run(){
      val br = new BufferedReader(new InputStreamReader(proc.getInputStream))
      var line : String =null;
      line = br.readLine()
      while (line != null) {
        processLine(line)
        line = br.readLine()
      }
      close
    }
  }
}

object CommandOutputHandler{
  def apply(file : File) : CommandOutputHandler = new CommandOutputHandler(
    writer = Some(new PrintWriter(new FileWriter(file))),
    closeWriter = true
  )
  val NULL = new CommandOutputHandler(writer = None)
  object NO_CONSUME_PROCESS_OUTPUT extends CommandOutputHandler(){
    override def redirectOutputRunnable(proc : Process) = new Runnable(){
      def run(){
      }
    }
  }
}

case class Command(outputHandler : CommandOutputHandler, workingDirectory : Option[File], args : String*) {

  def savedOutput = outputHandler.savedOutput
  def withOutput(handler : CommandOutputHandler) = new Command(
    outputHandler = handler,
    workingDirectory,
    args : _*
  )

  def withSavedOutput = withOutput(outputHandler.withSavedOutput)
  def withNoOutput = withOutput(CommandOutputHandler.NULL)

  private def startProc() : Process = {
    if (args.exists(_.trim == ""))
      Log.warn("Passed an empty argument in command \n" + this + "\nyou probably didn't wan't this")
    val procBuilder = new ProcessBuilder(args : _*)
    procBuilder.redirectErrorStream(true)
    workingDirectory.map(procBuilder.directory(_))
    Log.debug("cwd set to " + workingDirectory.map(_.getAbsolutePath))
    procBuilder.start
  }

  def execAsync() : (Process, Future[Int]) = {
    Log.debug("Executing cmd - " + toString)
    val proc = startProc()
    val outputThread = new Thread(outputHandler.redirectOutputRunnable(proc))
    outputThread.start
    (proc, future {outputThread.join; proc.waitFor})
  }

  def exec() : Int = {
    val proc = startProc
    outputHandler.redirectOutputRunnable(proc).run
    proc.waitFor
  }

  def asString = args.mkString(" ")
  override def toString = "Command: " + asString
}

object Command{
  def apply(args : String*) : Command = new Command(CommandOutputHandler(), None, args : _*)
  def apply(workingDirectory : Option[File], args : String*) : Command = new Command(CommandOutputHandler(), workingDirectory, args : _*)
}

object ScalaCommand {
  def apply(outputHandler : CommandOutputHandler, java : String, opts : List[String], classpath : String, klass : String, args : String*) : Command = {
    val allArgs : List[String] = List(
      java,
      "-Dscala.usejavacp=true",
      "-classpath",
      classpath) ::: opts :::
      List("scala.tools.nsc.MainGenericRunner",
      klass) ::: args.toList
    Command(outputHandler, None, allArgs :_*)
  }
}

object ScalaDocCmd {
  def apply(outputHandler : CommandOutputHandler, outputDir : File, java : String, classpath : String, opts : List[String], files : File*) : Command = {
    val allArgs : List[String] = List(
      java,
      "-Dscala.usejavacp=true",
      "-classpath",
      classpath) ::: opts :::
      "scala.tools.nsc.ScalaDoc" :: files.map(_.getAbsolutePath).toList
    Command(outputHandler, Some(outputDir), allArgs :_*)
  }
}
