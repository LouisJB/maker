package maker

import maker.utils.FileUtils._
import java.io.File
import java.io.BufferedReader
import java.util.Properties
import java.io.FileInputStream
import scala.collection.JavaConversions


case class Props(private val overrides : Map[String, String] = Map()){
  trait Property{
    def default : () => String
    def value : String = overrides.getOrElse(name, default())
    def name = {
      val objectName = getClass.getName
      objectName.substring(objectName.indexOf("$")+1, objectName.length()-1)
    }
  }

  class StringProperty(val default : () => String) extends Property{
    def apply() = value
  }
  class FileProperty(val default : () => String) extends Property{
    def apply() = file(value)
  }
  class OptionalProperty extends Property{
    val default = () => ""
    def apply() = value match {
      case "" => None
      case v => Some(v)
    }
  }
  object HttpProxyHost extends OptionalProperty
  object HttpProxyPort extends OptionalProperty
  object HttpNonProxyHosts extends OptionalProperty

  object ScalaHome extends FileProperty(() => "/usr/local/scala/")
  object JavaHome extends FileProperty(() => "/usr/local/jdk/")
  object IvyJar extends FileProperty(() => "/usr/share/java/ivy.jar")
  private val propertyMethods = this.getClass.getMethods.filter{
    m =>
      classOf[Property].isAssignableFrom(m.getReturnType) && m.getParameterTypes.isEmpty
  }

  lazy val properties = {
    Map() ++ propertyMethods.map{
      m =>
        m.getName -> m.invoke(this).asInstanceOf[Property]
    }
  }
  overrides.foreach{
    case (o, _) => 
      assert(propertyMethods.map(_.getName).toSet.contains(o), "Overiding non existant property " + o)
  }

  object Organisation extends StringProperty(() => "Acme Org")
  object Version extends StringProperty(() => "1.0-SNAPSHOT")
}

object Props{
  def apply(file : File) : Props = Props(propsFromFile(file))

  private def propsFromFile(propsFile:File) : Map[String, String] = {
    val path = propsFile.getAbsolutePath
    val p = new Properties()
    if(propsFile.exists) {
      p.load(new FileInputStream(propsFile))
    }

    Map() ++ JavaConversions.mapAsScalaMap(p.asInstanceOf[java.util.Map[String,String]])
  }

}
