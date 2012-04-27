package maker.task.tasks

import maker.project.Project
import org.apache.commons.io.FileUtils._
import maker.utils.FileUtils._
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.util.filter.FilterHelper
import org.apache.ivy.Ivy
import org.apache.ivy.core.retrieve.RetrieveOptions
import maker.task.{ProjectAndTask, TaskFailed, Task}
import maker.utils.{GAV, GroupAndArtifact, Log}
import xml.NodeSeq

case object UpdateTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    try {
      if (project.ivyFile.exists) {
        val confs = Array[String]("default", "compile", "test") // todo, need to handle confs / scopes properly in the near future... for now get all appropriate confs
        val artifactFilter = FilterHelper.getArtifactTypeFilter(Array[String]("jar", "war", "bundle", "source"))
        val resolveOptions = new ResolveOptions().setConfs(confs)
          .setValidate(true)
          .setArtifactFilter(artifactFilter)
        val ivy = Ivy.newInstance
        val settings = ivy.getSettings
        settings.addAllVariables(System.getProperties)
        ivy.configure(project.ivySettingsFile)
        val ivyFile = file(project.root, nameAndExt(project.ivyFile)._1 + "-dynamic.ivy")
        copyFile(project.ivyFile, ivyFile)

        val includes : List[NodeSeq] = project.allDeps.flatMap(_.additionalLibs).map(_.toIvyInclude)
        val excludes : List[NodeSeq] = project.allDeps.map(_.moduleId.toIvyExclude) ::: project.allDeps.flatMap(_.additionalExcludedLibs.map(_.toIvyExclude))

        replaceInFile(ivyFile, "${maker.module.excluded.libs}", (includes ::: excludes).mkString("\n"))

        ivy.configure(ivyFile)

        val report = ivy.resolve(ivyFile.toURI().toURL(), resolveOptions)
        val md = report.getModuleDescriptor
        ivy.retrieve(
          md.getModuleRevisionId(),
          project.managedLibDir.getPath + "/[artifact]-[revision](-[classifier]).[ext]",
          new RetrieveOptions()
            .setConfs(confs).setSync(true)
            .setArtifactFilter(artifactFilter))
        Right("OK")
      } else {
        Log.info("Nothing to update")
        Right("OK")
      }
    }
    catch {
      case e =>
        e.printStackTrace
        Left(TaskFailed(ProjectAndTask(project, this), e.getMessage))
    }
  }
}
