package plugin

import scala.collection.mutable.{Set => MSet, Map => MMap}
import collection.immutable.Map
import maker.utils.FileUtils
import java.io.{BufferedReader, BufferedWriter, File}
import maker.utils.Log

case class ProjectSignatures(persisted : File, private val sigs : MMap[File, Set[String]] = MMap()){
  //def changedFiles(): Set[File] = {
    //val olderSigs = ProjectSignatures.makeSignatureMap(persisted)
    //val changedFiles = Set() ++ sigs.keySet.filter{file => sigs.get(file) != olderSigs.getOrElse(file, Set[String]())}
    //}
  def += (sourceFile : File, sig : Set[String]) {
    sigs.update(sourceFile, sig)
  }

  def signature: Map[File, Set[String]] = Map[File, Set[String]]() ++ sigs
  def filesWithChangedSigs = {
    val olderSigs = ProjectSignatures.makeSignatureMap(persisted)
    val changedFiles = Set() ++ sigs.keySet.filter{file => (sigs(file) != olderSigs.getOrElse(file, Set[String]()))}
    Log.debug("Files with changed sigs " + changedFiles.mkString("\n\t", "\n\t", ""))
    sigs.foreach{
      case (file, sig) => 
        Log.debug("File = " + file)
        val os = olderSigs.getOrElse(file, Set[String]())
        Log.debug("Is same " + (os == sig))
        Log.debug("Is same " + (os == sig))
    }

    Log.debug("Sig changes\n" + changeAsString(olderSigs))
    changedFiles
  }

  def persist(){
    FileUtils.withFileWriter(persisted){
      writer : BufferedWriter =>
        sigs.foreach{
          case (sourceFile, sig) =>
            writer.write(sourceFile.getPath + "\n")
            sig.foreach{
              case sigLine =>
                writer.write("\t" + sigLine + "\n")
            }
        }
    }
  }

  private def files = sigs.keys.toList.sortWith(_.toString < _.toString)

  private def sigString(sig : Set[String], prefix : String = "\n\t\t", infix : String = "\n\t\t", postfix : String = "") = sig.toList.sortWith(_<_).mkString(prefix, infix, postfix)

  private def changeAsString(olderSigs : MMap[File, Set[String]]) = {
    val buff = new StringBuffer()
    files.foreach{
      file =>
        val oldSig = olderSigs.getOrElse(file, Set())
        val newSig = sigs.getOrElse(file, Set())
        val deletedSigs = oldSig.filterNot(newSig)
        val newSigs = newSig.filterNot(oldSig)
        buff.append("\n\t" + file + "\n")
        Log.debug((oldSig == newSig) + ", " + deletedSigs.size + ", " + newSigs.size)
        buff.append(sigString(deletedSigs, "\n\t\t-", "\n\t\t-"))
        buff.append(sigString(newSigs, "\n\t\t+", "\n\t\t+"))
    }
    buff.toString
  }

  override def toString = {
    val buff = new StringBuffer()
    files.foreach{
      file =>
        val signature = sigs(file)
        buff.append("\n\t" + file)
        buff.append(sigString(sigs(file)))
    }
    buff.toString
  }
}

object ProjectSignatures{
  def makeSignatureMap(file : File) : MMap[File, Set[String]] = {
    var sigs = MMap[File, Set[String]]()
    var sourceFile : File = null
    var lines = Set[String]()

    val SourceFile = "([^\t]*)".r
    val Line = "\t([^\t]*)".r
    FileUtils.withFileReader(file){
      in : BufferedReader => {
        var line = in.readLine()
        while (line != null){
          line match {
            case SourceFile(fileName) => {
              if (sourceFile != null){
                sigs.update(sourceFile, lines)
                lines = Set[String]()
              }
              sourceFile = new File(fileName)
            }
            case Line(line) => lines += line
            case _ => throw new Exception("Don't understand line " + line + " when parsing project signatures file")
          }
          line = in.readLine()
        }
        if (sourceFile != null){
          sigs.update(sourceFile, lines)
        }
      }
    }
    sigs
  }
}
