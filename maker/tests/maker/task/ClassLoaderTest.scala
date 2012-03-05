package maker.task

import org.scalatest.FunSuite
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.Global
import maker.utils.FileUtils._
import java.io.File
import scala.collection.immutable.List
import scala.tools.nsc.io.PlainDirectory
import scala.tools.nsc.io.Directory


class ClassLoaderTest extends FunSuite{

  val rootDir = new File("classloadertest")

  val src_2_9 = """
    package foo
    class Foo{
      def fun(l : List[Int]) = l.head
    }
  """

    
  //test("2.9 feature compiles"){
    //
    //val comp = make_2_9_Compiler
    //val srcFile = makeSrcFile
    //new comp.Run() compile List(srcFile.getPath)
    //assert(! comp.reporter.hasErrors)
    //}

  test("2.9 feature does not compile with 2.8 libs"){

    val comp = make_2_8_Compiler
    val srcFile = makeSrcFile
    new comp.Run() compile List(srcFile.getPath)
    assert(comp.reporter.hasErrors)
  }

  def makeSrcFile = {
    val file = new File(rootDir, "foo/File.scala")
    recursiveDelete(file.getParentFile)
    file.getParentFile.mkdirs
    writeToFile(file, src_2_9)
    file
  }

  def make_2_8_Compiler = {
    val settings = new Settings
    settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(new File(rootDir, "out"))))
    println(settings)
    settings.usejavacp.value=false
    val scalaAndJavaLibs = System.getProperty("sun.boot.class.path")
    val javaLibs=scalaAndJavaLibs.split(":").filterNot(_.contains("scala")).mkString(":")
    System.setProperty("sun.boot.class.path", javaLibs)
    val scalaLibs= "classloadertest/2.8.1-lib/scala-compiler.jar:classloadertest/2.8.1-lib/scala-library.jar"
    scalaLibs.split(":").foreach{
      name => 
      assert(new File(name).exists)
    }
    System.setProperty("java.class.path",scalaLibs)
    System.setProperty("scala.home","/usr/local/scala-2.8.1.final")
    //System.setProperty("scala.usejavacp", "false")
    settings.javabootclasspath.value = javaLibs
    settings.classpath.value="."
    settings.bootclasspath.value=scalaLibs
    println("javabootclasspath" + settings.javabootclasspath.value)
    println("javaextdirs" + settings.javaextdirs.value)
    println("bootclasspath" + settings.bootclasspath.value)
    println("extdirs" + settings.extdirs.value)
    println("classpath" + settings.classpath.value)
    println("sourcepath" + settings.sourcepath.value)
    val pr = new scala.tools.util.PathResolver(settings)
    val res = pr.result
    println(pr.Calculated)
    val reporter = new ConsoleReporter(settings)
    new Global(settings, reporter) 
  }
  def make_2_9_Compiler = {
    val settings = new Settings
    settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(new File(rootDir, "out"))))
    settings.usejavacp.value=false
    val reporter = new ConsoleReporter(settings)
    new Global(settings, reporter) 
  }


}
