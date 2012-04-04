package maker.task

import maker.project.Project
import maker.os.Command
import maker.utils.Log
import java.io.File
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.util.filter.FilterHelper
import org.apache.ivy.Ivy
import org.apache.ivy.core.retrieve.RetrieveOptions

case object UpdateExternalDependencies extends Task {

  def execOld(project : Project, acc : List[AnyRef], parameters : Map[String, String] = Map()) = {
    import project._
    managedLibDir.mkdirs
    Log.info("Updating " + name)
    def commands() : List[Command] = {
      val java = props.JavaHome().getAbsolutePath + "/bin/java"
      val ivyJar = props.IvyJar().getAbsolutePath
      def proxyParameter(parName : String, property : Option[String]) : List[String] = {
        property match {
          case Some(thing) => (parName + "=" + thing) :: Nil
          case None => Nil
        }
      }
        
      val parameters = List(java) ::: 
        proxyParameter("-Dhttp.proxyHost", project.props.HttpProxyHost()) :::
        proxyParameter("-Dhttp.proxyPort", project.props.HttpProxyPort()) :::
        proxyParameter("-Dhttp.nonProxyHosts", project.props.HttpNonProxyHosts()) :::
        List("ivy.checksums=") :::
        ("-jar"::ivyJar::"-settings"::ivySettingsFile.getPath::"-ivy"::ivyFile.getPath::"-retrieve"::Nil) 

      List(
        Command(parameters ::: ((managedLibDir.getPath + "/[artifact]-[revision](-[classifier]).[ext]")::"-sync"::"-types"::"jar"::Nil) : _*),
        Command(parameters ::: ((managedLibDir.getPath + "/[artifact]-[revision](-[classifier]).[ext]")::"-types"::"bundle"::Nil) : _*)
        // removed, this is to be replaced by using Ivy direction not via bash
        //Command(parameters ::: ((managedLibDir.getPath + "/[artifact]-[revision]-source(-[classifier]).[ext]")::"-types"::"source"::Nil) : _*)
      )
    }
    if (ivyFile.exists){
      def rec(commands : List[Command]) : Either[TaskFailed, AnyRef] = 
        commands match {
          case Nil => Right("OK")
          case c :: rest => {
            c.exec() match {
              case (0, _) => rec(rest)
              case (_, error) => {
                Log.info("Command failed\n" + c)
                Left(TaskFailed(ProjectAndTask(project, this), error))
              }
            }
          }
        }
      rec(commands())
    } else {
      Log.info("Nothing to update")
      Right("OK")
    }
  }

  def exec(project : Project, acc : List[AnyRef], parameters : Map[String, String] = Map()) = {
    try {
      if (project.ivyFile.exists){
        val confs = Array[String]("default")
        val artifactFilter = FilterHelper.getArtifactTypeFilter(Array[String]("jar", "war", "bundle", "source"))

        val resolveOptions = new ResolveOptions().setConfs(confs)
                      .setValidate(true)
                      .setArtifactFilter(artifactFilter)
        val ivy = Ivy.newInstance
        val settings = ivy.getSettings
        settings.addAllVariables(System.getProperties)
        ivy.configure(project.ivySettingsFile)
        val report = ivy.resolve(project.ivyFile.toURI().toURL(), resolveOptions)
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
    } catch {
      case e => 
        e.printStackTrace
        Left(TaskFailed(ProjectAndTask(project, this), e.getMessage))
    }
  }
}
