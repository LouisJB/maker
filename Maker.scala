import maker.project.Project
import maker.Props
import java.io.File
import maker.utils.FileUtils._
import maker.Maker._

:power 

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

def compileContinuously = mkr.~(mkr.testCompile _)
def testContinuously(klass : String = "") = {
  if (klass == "")
    mkr.~(mkr.test _)
  else{
    val test = () ⇒ mkr.testClassOnly(klass)
    mkr.~(test)
  }
}

println("\nMaker v" + MAKER_VERSION + "\n")
repl.setPrompt("maker v" + MAKER_VERSION + ">")

