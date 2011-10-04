package maker.task

import maker.project.Project
import java.io.File
import maker.os.{Command, Environment}


object UpdateSignatures{
  val CompiledFromRegex = """Compiled from \"(\w+).*""".r

  def apply(project: Project) : Set[File] = {
    import Environment._
    import project._

    val classFilesToProcess = project.classFiles.filter(_.lastModified() > project.signatures.timestamp)
    val shortClassNames =
      classFilesToProcess.map {
        cf =>
          cf.getPath.substring(outputDir.getPath.length + 1).replace("/", ".").replace(".class", "")
      }.toList

    var sourceFileSigs = Map[File, List[String]]()
    var currentSourceFile: Option[File] = None
    shortClassNames.grouped(40).foreach {
      group =>
        val args = List(javap, "-classpath", outputDir.getPath) ::: group
        val cmd = Command(args: _*)
        cmd.exec match {
          case (0, sigs) => {
            sigs.split("\n").foreach {
              case CompiledFromRegex(scalaFile) =>
                currentSourceFile = Some(new File(scalaFile))
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
