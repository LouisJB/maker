package maker.task

import maker.utils.FileUtils._
import maker.project._
import maker.utils.Log
import java.util.Date
import org.apache.ivy.core.publish.PublishOptions
import org.apache.ivy.util.filter.FilterHelper
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.Ivy
import org.apache.ivy.core.IvyContext

case object PublishTask extends Task {
  def exec(project: Project, acc: List[AnyRef], parameters : Map[String, String] = Map()) = {

    val homeDir = project.props.HomeDir()
    val moduleLocal = file(homeDir, ".ivy2/maker-local/" + project.name)

    // paceholder, todo, implement some automation equivalent to:
    // $ java -Dhttp.proxyHost=host -Dhttp.proxyPort=port -Dhttp.nonProxyHosts=
    //   -jar ~/repos/maker/libs/ivy-2.2.0.jar -debug -ivy utils/maker-ivy.xml -settings maker-ivysettings.xml -publish resolvername -revision 1.2.3 -publishpattern /home/louis/.ivy2/maker-local/utils/jars/utils.jar

    try {
      if (project.ivyFile.exists){
        val confs = Array[String]("default")
        val artifactFilter = FilterHelper.getArtifactTypeFilter(Array[String]("xml", "jar", "bundle", "source"))
        val resolveOptions = new ResolveOptions().setConfs(confs)
          .setValidate(true)
          .setArtifactFilter(artifactFilter)
        val ivy = Ivy.newInstance
        val settings = ivy.getSettings
        settings.addAllVariables(System.getProperties)
        ivy.configure(project.ivySettingsFile)
        //val contextSettings = IvyContext.getContext().getSettings()
        settings.setVariable("maker.ivy.publish.username", project.props.Username(), true)
        settings.setVariable("maker.ivy.publish.password", project.props.Password(), true)
        val report = ivy.resolve(project.ivyFile.toURI().toURL(), resolveOptions)
        val md = report.getModuleDescriptor

        val resolverName = parameters.getOrElse("publishResolver", project.props.DefaultPublishResolver().getOrElse("default"))
        val version = parameters.getOrElse("version", project.props.Version())
        //project.managedLibDir.getPath + "/[artifact]-[revision](-[classifier]).[ext]",
        import scala.collection.JavaConversions._
        val srcArtifactPattern = List(
          moduleLocal.getAbsolutePath + "/poms/pom.xml",
          moduleLocal.getAbsolutePath + "/jars/" + project.name + ".jar")
        Log.info("Publish for project" + project.name)
        ivy.publish(
          md.getModuleRevisionId(),
          srcArtifactPattern,
          resolverName,
          new PublishOptions()
            .setConfs(confs).setOverwrite(true)
            .setPubrevision(version)
            .setPubdate(new Date()))
        Right("OK")
      }
      else {
        Log.info("Nothing to publish")
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
