package maker.task.tasks

import maker.project.Project
import maker.utils.FileUtils._
import maker.task.{ProjectAndTask, TaskFailed, Task}
import maker.utils.{GAV, GroupAndArtifact, Log}
import org.apache.commons.io.FileUtils._
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.util.filter.FilterHelper
import org.apache.ivy.Ivy
import org.apache.ivy.core.retrieve.RetrieveOptions
import xml.NodeSeq

case object UpdateTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    try {
      project.ivyGeneratedFile match {
        case Some(ivyGeneratedFile) => {
          val confs : Array[String] = parameters.getOrElse("configurations", "default").split(":")
          val artifactFilter = FilterHelper.getArtifactTypeFilter(Array[String]("jar", "war", "bundle", "source"))
          val resolveOptions = new ResolveOptions().setConfs(confs)
            .setValidate(true)
            .setArtifactFilter(artifactFilter)
          val ivy = Ivy.newInstance
          val settings = ivy.getSettings
          settings.addAllVariables(System.getProperties)
          ivy.configure(project.ivySettingsFile)

          ivy.configure(ivyGeneratedFile)
          val report = ivy.resolve(ivyGeneratedFile.toURI().toURL(), resolveOptions)
          val md = report.getModuleDescriptor
          ivy.retrieve(
            md.getModuleRevisionId(),
            project.managedLibDir.getPath + "/[artifact]-[revision](-[classifier]).[ext]",
            new RetrieveOptions()
              .setConfs(confs).setSync(true)
              .setArtifactFilter(artifactFilter))
          Right("OK")
        }
        case None => {
          Log.info("Nothing to update")
          Right("OK")
        }
      }
    }
    catch {
      case e =>
        e.printStackTrace
        Left(TaskFailed(ProjectAndTask(project, this), e.getMessage))
    }
  }
}
