package maker.project

import java.io.File
import maker.utils.FileUtils._

case class ProjectState(project : Project){
  lazy val classFileDependencies = plugin.ClassFileDependencies(new File(project.makerDirectory, "dependencies"))
  lazy val signatures = plugin.ProjectSignatures(new File(project.makerDirectory, "signatures"))
  lazy val sourceToClassFiles = plugin.SourceClassFileMapping(new File(project.makerDirectory, "sourceToClassFiles"))

  def lastModificationTime(files : Set[File]) = files.toList.map(_.lastModified).sortWith(_ > _).headOption

  private def filterChangedSrcFiles(files : Set[File], modTime : Option[Long]) = {
    modTime match {
      case Some(time) => files.filter(_.lastModified > time)
      case None => files
    }
  }

  def compilationTime: Option[Long] = lastModificationTime(project.classFiles)
  def javaCompilationTime: Option[Long] = lastModificationTime(project.javaClassFiles)
  def testCompilationTime: Option[Long] = lastModificationTime(project.testClassFiles)
  def changedSrcFiles = filterChangedSrcFiles(project.srcFiles(), compilationTime)
  def changedJavaFiles = filterChangedSrcFiles(project.javaSrcFiles(), javaCompilationTime)
  def changedTestFiles = filterChangedSrcFiles(project.testSrcFiles(), testCompilationTime)
  def deletedSrcFiles = sourceToClassFiles.sourceFiles.filterNot(project.srcFiles()).filter{sf => project.srcDirs.exists(sf.isContainedIn(_))}
  def deletedTestFiles = sourceToClassFiles.sourceFiles.filterNot(project.testSrcFiles()).filter{sf => project.testDirs.exists(sf.isContainedIn(_))}
  def updateSignatures : Set[File] = {
    val changedFiles = signatures.filesWithChangedSigs
    signatures.persist
    changedFiles
  }

  def classFilesFor(srcFile : File) = sourceToClassFiles.map.getOrElse(srcFile, Set[File]())
}

