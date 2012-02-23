package maker.utils

/**
 * Simple case class representing a library dependency definition (from a Maven repo)
 */
case class DependencyLib(
    name : String,
    version : String,
    org : String,
    classifierOpt : Option[String] = None) {

  val classifier = classifierOpt.getOrElse("")

  override def toString = "Name: %s Version: %s Org: %s Classifier: %s".format(name, version, org, classifier)
}
