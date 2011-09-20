package invoke

import tools.nsc.Main


object Invoke extends App{
  println("Hello")

  Main.main(Array("-Xplugin:../out/artifacts/plugin_jar/plugin.jar", "test-src/test/Test.scala", "test-src/test/sub/Bar.scala"))

}