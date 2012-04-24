package maker.task.tasks

import maker.project.Project
import org.apache.commons.io.FileUtils._
import maker.utils.FileUtils._
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.util.filter.FilterHelper
import org.apache.ivy.Ivy
import org.apache.ivy.core.retrieve.RetrieveOptions
import maker.utils.{GroupAndArtifact, Log}
import maker.task.{ProjectAndTask, TaskFailed, Task}

case object UpdateTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters: Map[String, String] = Map()) = {
    try {
      if (project.ivyFile.exists) {
        val confs = Array[String]("default")
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

        val excludedBinaryModules : List[GroupAndArtifact] = project.allDeps.map(_.moduleId) ::: project.allDeps.flatMap(_.additionalExcludedLibs.map(_.toGroupAndArtifact))
        val excludes = excludedBinaryModules.map(e => <exclude org={e.groupId.id} module={e.artifactId.id} />.toString)

        replaceInFile(ivyFile, "${maker.module.excluded.libs}", excludes.mkString("\n"))
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
