package maker.task

import maker.project.Project
import java.io.File
import maker.os.{Command, Environment}
import collection.immutable.Map


object UpdateSignatures {
  val CompiledFromRegex = """Compiled from \"(\w+).*""".r

  def apply(project: Project): Set[File] = {
    import Environment._
    import project._

    val sourceFilesByBaseName: Map[String, File] = project.srcFiles().map {
      file =>
        file.getName.split('.').head -> file
    }.toMap
    val classFilesToProcess = project.classFiles.filter(_.lastModified() > project.signatures.timestamp)

    var sourceFileSigs = Map[File, List[String]]()
    var currentSourceFile: Option[File] = None
    val shortClassNames =
      classFilesToProcess.map {
        cf =>
          cf.getPath.substring(outputDir.getPath.length + 1).replace("/", ".").replace(".class", "")
      }.toList
    shortClassNames.grouped(40).foreach {
      group =>
        val args = List(javap, "-classpath", outputDir.getPath) ::: group
        val cmd = Command(args: _*)
        cmd.exec match {
          case (0, sigs) => {
            sigs.split("\n").foreach {
              case CompiledFromRegex(baseName) =>
                currentSourceFile = Some(sourceFilesByBaseName(baseName))
              case line =>
                currentSourceFile match {
                  case Some(file) =>
                    sourceFileSigs = sourceFileSigs.updated(file, (line :: sourceFileSigs.getOrElse(file, Nil)))
                  case None =>
                    throw new Exception("No source file in line " + line)
                }
            }
          }

          case (err, _) =>
            throw new Exception("Error " + err + " when executing " + cmd)
        }
    }

    val sourceFilesWithChangedSignature = sourceFileSigs.keySet.filter {
      sourceFile =>
        val hash = sourceFileSigs(sourceFile).sortWith(_ < _).hashCode
        project.signatures +=(sourceFile, hash)
    }
    project.signatures.persist
    sourceFilesWithChangedSignature

  }
}
