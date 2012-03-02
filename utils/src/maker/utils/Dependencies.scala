package maker.utils

import org.apache.ivy.ant.IvyMakePom
import org.apache.ivy.plugins.parser.m2.PomWriterOptions


/**
 * Simple case class representing a library dependency definition (from a Maven repo)
 */
case class DependencyLib(
    name : String,
    artifact : String,
    version : String,
    org : String,
    scope : String,
    classifierOpt : Option[String] = None) {

  val classifier = classifierOpt.getOrElse("")

  override def toString = "Name: %s Version: %s Org: %s Classifier: %s".format(name, version, org, classifier)

  def toIvyMavenDependency : IvyMakePom#Dependency = {
    val ivyMakePom : IvyMakePom = new IvyMakePom
    val dep = (new ivyMakePom.Dependency())
    dep.setGroup(name)
    dep.setArtifact(artifact)
    dep.setVersion(version)
    dep.setScope(scope)
    dep.setOptional(false)
    dep
  }

  def toIvyPomWriterExtraDependencies : PomWriterOptions.ExtraDependency =
    new PomWriterOptions.ExtraDependency(name, artifact, version, scope, false)
}

object DependencyLib {
  implicit def toIvyMavenDependency(lib : DependencyLib) : IvyMakePom#Dependency =
    lib.toIvyMavenDependency
  implicit def toIvyPomWriterExtraDependencies(lib : DependencyLib) : PomWriterOptions.ExtraDependency =
    lib.toIvyPomWriterExtraDependencies
}
