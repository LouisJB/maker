package plugin

import scala.collection.mutable.{Set => MSet, Map => MMap}
import collection.immutable.Map
import maker.utils.FileUtils
import java.io.{BufferedReader, BufferedWriter, File}

case class ProjectSignatures(private val sigs : MMap[File, Set[String]] = MMap()){
  def changedFiles(olderSigs : ProjectSignatures): Set[File] = Set() ++ sigs.keySet.filter{file => sigs.get(file) != olderSigs.sigs.get(file)}
  def += (sourceFile : File, sig : Set[String]) {
    sigs.update(sourceFile, sig)
  }

  def signature: Map[File, Set[String]] = Map[File, Set[String]]() ++ sigs
  def persist(file : File){
    FileUtils.withFileWriter(file){
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

  def changeAsString(olderSigs : ProjectSignatures) = {
    val buff = new StringBuffer()
    files.foreach{
      file =>
        val oldSig = olderSigs.sigs.getOrElse(file, Set())
        val newSig = sigs.getOrElse(file, Set())
        val deletedSigs = oldSig.filterNot(newSig)
        val newSigs = newSig.filterNot(oldSig)
        buff.append("\n\t" + file + "\n")
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
  def apply(file : File) : ProjectSignatures = {
    var sigs = ProjectSignatures()
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
                sigs += (sourceFile, lines)
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
          sigs += (sourceFile, lines)
        }
      }
    }
    sigs
  }
}
