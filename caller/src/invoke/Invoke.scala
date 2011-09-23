package invoke

import tools.nsc.Main
import plugin.WriteDependencies
import java.io.File


object Invoke extends App{
  val sourceFiles = List("caller/test-src/test/Test.scala", "caller/test-src/test/sub/Bar.scala")
  Main.main(("-Xplugin:out/artifacts/plugin_jar/plugin.jar" :: sourceFiles).toArray)

  val dependencyFiles = sourceFiles.map(WriteDependencies.dependencyFile _)


  dependencyFiles.map(new File(_)).foreach{
    f =>
      System.err.println(f + ", " + f.exists())
  }
  println("Finished")
  System.out.flush()
}