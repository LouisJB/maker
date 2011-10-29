package plugin

import scala.collection.mutable.{Set => MSet, Map => MMap}
import collection.immutable.Map
import maker.utils.FileUtils
import java.io.{BufferedReader, BufferedWriter, File}

case class ClassSignature(packages : List[String], classes : List[String]){
  import ClassSignature._
  override def toString = packageTitle + packages.mkString(".") + classTitle + classes.mkString(".")
}

object ClassSignature{
  private val packageTitle = "package "
  private val classTitle = ", class "
  val SignatureRegex = (packageTitle + "([^,]*)" + classTitle + "(.*)").r
  private def split(text : String) = text.split('.').toList.filterNot(_ == "")
  def apply(text : String) : ClassSignature = text match {
    case SignatureRegex(packages, classes) => ClassSignature(split(packages), split(classes))
    case _ => throw new Exception("Can't convert " + text + " to class signature")
  }
}

case class MethodSignature(name : String, klass : ClassSignature, arguments : List[ClassSignature], returnType : ClassSignature) extends Ordered[MethodSignature]{
  def compare(that: MethodSignature) = toString.compareTo(that.toString)
  override def toString : String = (List(name, klass, returnType) ::: arguments).mkString(":")
}

object MethodSignature{
  def apply(text : String) : MethodSignature = {
    text.split(':').toList match {
      case name :: klass :: returnType :: arguments => {
        MethodSignature(name, ClassSignature(klass), arguments.map(ClassSignature(_)), ClassSignature(returnType))
      }
      case _ => throw new Exception("Can't make MetodSignature from " + text)
    }
  }
}

case class ProjectSignatures(private val sigs : MMap[File, MMap[ClassSignature, MSet[MethodSignature]]] = MMap()){
  def changedFiles(olderSigs : ProjectSignatures): Set[File] = Set() ++ sigs.keySet.filter{file => sigs.get(file) != olderSigs.sigs.get(file)}
  def += (sourceFile : File, klass : ClassSignature, method : MethodSignature) {
    val fileSignature = sigs.getOrElse(sourceFile, MMap[ClassSignature, MSet[MethodSignature]]())
    val classSignature = fileSignature.getOrElse(klass, MSet[MethodSignature]())
    classSignature += method
    fileSignature.update(klass, classSignature)
    sigs.update(sourceFile, fileSignature)
  }

  def signature: Map[File, Map[ClassSignature, Set[MethodSignature]]] = {
    Map[File, Map[ClassSignature, Set[MethodSignature]]]() ++ sigs.map{
      case (file, fileSig) => file -> (Map[ClassSignature, Set[MethodSignature]]() ++ fileSig.map{
        case (klass, methods) => klass -> (Set[MethodSignature]() ++ methods)
      })
    }
  }
  def persist(file : File){
    FileUtils.withFileWriter(file){
      writer : BufferedWriter =>
        signature.foreach{
          case (sourceFile, classSignatures) =>
            writer.write(sourceFile.getPath + "\n")
            classSignatures.foreach{
              case (klass, methods) =>
                writer.write("\t" + klass + "\n")
                writer.write(methods.toList.sortWith(_<_).mkString("\t\t", "\n\t\t", "\n"))
            }
        }
    }
  }
}

object ProjectSignatures{
  def apply(file : File) : ProjectSignatures = {
    var sigs = ProjectSignatures()
    var sourceFile : File = null
    var klassSignature : ClassSignature = null

    val SourceFile = "([^\t]*)".r
    val ClassName = "\t([^\t]*)".r
    val Method = "\t\t([^\t]*)".r
    FileUtils.withFileReader(file){
      in : BufferedReader => {
        var line = in.readLine()
        while (line != null){
          line match {
            case SourceFile(fileName) => sourceFile = new File(fileName)
            case ClassName(klass) => klassSignature = ClassSignature(klass)
            case Method(method) => {
              sigs += (sourceFile, klassSignature, MethodSignature(method))
            }

            case _ => throw new Exception("Don't understand line " + line + " when parsing project signatures file")
          }
          line = in.readLine()
        }
      }
    }
    sigs
  }
}