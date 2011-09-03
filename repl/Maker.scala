import java.io.File
import maker._

val jars = List(
  new File("copy/jars/log4j-1.2.16.jar"),
  new File("copy/jars/scalatest_2.9.0-1.4.1.jar"),
  new File("copy/jars/scalaz-core_2.9.0-1-6.0.1.jar"),
  new File("copy/jars/slf4j-api-1.6.1.jar"),
  new File("copy/jars/slf4j-log4j12-1.6.1.jar")
)
def copy = List(Command("mkdir", "copy"), Command("cp", "-r", "src", "jars", "copy")).foreach(_.exec)
val proj = Project("Maker", new File("copy"), List(new File("copy/src")), jars, new File("copy/out"), new File("copy/out-jar"))
import proj._
