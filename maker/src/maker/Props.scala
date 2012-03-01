package maker

import maker.utils.FileUtils._
import java.io.File
import java.io.BufferedReader
import java.util.Properties
import java.io.FileInputStream
import scala.collection.JavaConversions
import java.io.OutputStream

case class Props(private val overrides : Map[String, String] = Map()){
  trait Property{
    def default : () => String
    def value : String = overrides.getOrElse(name, default())
    def name = {
      val objectName = getClass.getName
      objectName.substring(objectName.indexOf("$")+1, objectName.length()-1)
    }
    override def toString = name + "=" + value
  }
  class StringProperty(val default : () => String) extends Property{
    def apply() = value
  }
  class FileProperty(val default : () => String) extends Property{
    def apply() = file(value)
  }
  trait OptionalProperty extends Property{
    val default = () => ""
    def apply() = value match {
      case "" => None
      case v => Some(v)
    }
  }

  override def toString = 
    "Properties:\n" + properties.mkString("\n")
  
  /**
   * Actual properties start here
   */
  object HttpProxyHost extends OptionalProperty
  object HttpProxyPort extends OptionalProperty
  object HttpNonProxyHosts extends OptionalProperty

  object MakerHome extends StringProperty(() =>
    Option(System.getProperty("maker.home")).getOrElse{throw new Exception("maker.home System property most be set")}
  )

  val httpProperties : List[(String, String)]= List(HttpProxyHost, HttpProxyPort, HttpNonProxyHosts).flatMap{
    case prop => List(prop.name).zip(prop())
  }
  
  val javaHomeEnv = Option(System.getenv("JAVA_HOME")).orElse(Option(System.getenv("JDK_HOME")))
  object ScalaHome extends FileProperty(() => "/usr/local/scala/")
  object JavaHome extends FileProperty(() => javaHomeEnv.getOrElse("/usr/local/jdk/"))
  object IvyJar extends FileProperty(() =>
    MakerHome() + "/libs/ivy-2.2.0.jar"
  )
  object ScalaVersion extends StringProperty(() => "2.9.1")

  object HomeDir extends FileProperty(() => System.getProperty("user.home"))

  private val propertyMethods = this.getClass.getMethods.filter{
    m =>
      classOf[Property].isAssignableFrom(m.getReturnType) && m.getParameterTypes.isEmpty
  }

  /**
   * Compilation output is tee'd here from the repl, so we can 
   * quickfix error in Vim
   * The file is emptied before each compilation
   */
  object VimErrorFile extends FileProperty(() => "vim-compile-output")

  object CompilationOutputStream extends OutputStream{
    import java.io.FileOutputStream
    import java.io.PrintStream
    import org.apache.commons.io.output.TeeOutputStream

    private def makeTeeStream = {
      new PrintStream(
        new TeeOutputStream(
          Console.err, 
          new PrintStream(new FileOutputStream(VimErrorFile()))
        )
      )
    }
    var teeErr = makeTeeStream

    def emptyVimErrorFile{
      VimErrorFile().delete
      teeErr = makeTeeStream
    }
    def write(b : Int){
      teeErr.write(b)
    }
  }

  object Organisation extends StringProperty(() => "Acme Org")
  object Version extends StringProperty(() => "1.0-SNAPSHOT")
  
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

