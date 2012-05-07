import maker.project.Project
import maker.utils.FileUtils._
import maker.Props
import maker.utils.Log._
import java.io.File

val MAKER_VERSION = ".1"

System.setProperty("scala.usejavacp", "false")
System.setProperty("log4j.ignoreTCL", "true")

import maker.Maker._

def compileContinuously = mkr.~(mkr.testCompile _)
def testContinuously(klass : String = "") = {
  if (klass == "")
    mkr.~(mkr.test _)
  else{
    val test = () ⇒ mkr.testClassOnly(klass)
    mkr.~(test)
  }
}

println("\nMaker v" + MAKER_VERSION)

