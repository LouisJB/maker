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
import java.net.URLClassLoader
import java.lang.String

//import scala.util.URLClassLoader
import scala.tools.util.PathResolver
import java.net.URL


object ClassLoaderTest {
  def main(args : Array[String]){
    new ClassLoaderTest().execute()
  }
}

class ClassLoaderTest extends FunSuite{

  val rootDir = new File("classloadertest")
  val bootClassLoader = new URLClassLoader(Array[URL](), null)

  val src_2_9 = """
    package foo
    class Foo{
      def fun(l : List[Int]) = l.tail
    }
  """

    
  //test("2.9 feature compiles"){
    //
    //val comp = make_2_9_Compiler
    //val srcFile = makeSrcFile
    //new comp.Run() compile List(srcFile.getPath)
    //assert(! comp.reporter.hasErrors)
    //}

  val jars_2_8 = findJars(new File("classloadertest/2.8.1-lib"))
  val jars_2_9 = findJars(new File("classloadertest/2.9.1-lib"))
  val jars_2_10 = findJars(new File("classloadertest/2.10.0-lib"))

  class BootClassLoader(parent : ClassLoader) extends ClassLoader(parent){
    override def loadClass(name: String, resolve : Boolean) = {
      println("Loading class " + name + " in boot class loader")
      if (name.startsWith("scala."))
        throw new ClassNotFoundException(name)
      else
        super.loadClass(name, resolve)
    }
  }
  class MyClassLoader(urls : Array[URL]) extends URLClassLoader(urls, new BootClassLoader(getClass.getClassLoader)){
    //override def findClass(name: String) = throw new ClassNotFoundException()
    override def loadClass(name : String, resolve : Boolean) = {
      println("Loading " + name + " in main class loader")
      val klass = super.loadClass(name, resolve)
        println("Have class")
      klass
    }
}

  test("2.9 features again"){
  
    val scalaAndJavaLibs = System.getProperty("sun.boot.class.path")
    val javaLibs=scalaAndJavaLibs.split(":").filterNot(_.contains("scala")).mkString(":")
    val scalaJars = jars_2_9
    val javaJars = javaLibs.split(":").map(new File(_)).toList

    //val loader = new URLClassLoader((scalaJars ::: javaJars).map(_.toURI.toURL).toArray, null)
    val loader = new MyClassLoader((scalaJars.toList ::: javaJars).map(_.toURI.toURL).toArray)
    val t = new Thread(new Runnable(){
      def run(){


        val settingsClass = loader.loadClass("scala.tools.nsc.Settings")
        val settings = settingsClass.newInstance.asInstanceOf[AnyRef]
      //println("******* Default settings ")
      //println(settings.javabootclasspath.value)
      // 
      //
      //
      //settings.javabootclasspath.value = javaLibs
      //settings.usejavacp.value = false
      //settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(new File(rootDir, "out"))))
      //println("Original classpath value " + settings.classpath.value)
      ////settings.classpath.value=scalaJars.map(_.getPath).mkString(":")
      //val newClasspath=settings.classpath.value.toString.split(":").filter{
        //jar => 
        //jar.contains("compiler") || jar.contains("scala-lib")
        //}.mkString(":")
        ////settings.classpath.value=settings.classpath.value.toString
        //settings.classpath.value=newClasspath
        //println("new classpath value " + settings.classpath.value)
        val scalaLibs=scalaJars.map(_.getPath).mkString(":")
        //settings.classpath.value=scalaLibs
        //settings.javabootclasspath.value = javaLibs + ":" + scalaLibs
        //settings.usejavacp.value = true
        //
        //println("new new classpath value " + settings.classpath.value)
        //
        //
        //val srcFile = makeSrcFile
        val systemClass = loader.loadClass("java.lang.System")
        val setPropertyMethod = systemClass.getMethod("setProperty", classOf[String], classOf[String])
        val getPropertyMethod = systemClass.getMethod("getProperty", classOf[String])
        setPropertyMethod.invoke(null, "scala.usejavacp", "false")
        setPropertyMethod.invoke(null, "sun.boot.class.path", javaLibs)
        setPropertyMethod.invoke(null, "java.class.path", javaLibs + ":" + scalaLibs)
        //setPropertyMethod.invoke(null, "scala.home","/usr/local/scala-2.10.0-M2.final")
        setPropertyMethod.invoke(null, "scala.home","/usr/local/scala-2.9.1.final")
        val pathResolverClass = loader.loadClass("scala.tools.util.PathResolver")
        val pathResolverConstructor = pathResolverClass.getConstructor(settingsClass)
        val pr = pathResolverConstructor.newInstance(settings)//.asInstanceOf[PathResolver]

        val classpathMethod = settingsClass.getMethod("classpath")
        val classpath = classpathMethod.invoke(settings)
        val classpathSetterMethod = classpath.getClass.getMethod("value_$eq", classOf[Object])
        classpathSetterMethod.invoke(classpath, javaLibs + ":" + scalaLibs)

        val bootclasspathMethod = settingsClass.getMethod("javabootclasspath")
        val bootclasspath = bootclasspathMethod.invoke(settings)
        val bootclasspathSetterMethod = bootclasspath.getClass.getMethod("value_$eq", classOf[Object])
        bootclasspathSetterMethod.invoke(bootclasspath, javaLibs + ":" + scalaLibs)
      //println("System prop " + System.getProperty("scala.home"))
    //println("system prop " + getPropertyMethod.invoke(null, "scala.home"))
  //
  //val pathResolverObjectClass = loader.loadClass("scala.tools.util.PathResolver$")
  //val prObjectConstructors = pathResolverObjectClass.getDeclaredConstructors
  //prObjectConstructors(0).setAccessible(true)
  //val prObject = prObjectConstructors(0).newInstance().asInstanceOf[scala.tools.util.PathResolver$]
  //
  //val defaultsObjectClass = loader.loadClass("scala.tools.util.PathResolver$Defaults$")
  //val defaultsObjectConstructors = defaultsObjectClass.getDeclaredConstructors
  //defaultsObjectConstructors(0).setAccessible(true)
  //val defaults = defaultsObjectConstructors(0).newInstance().asInstanceOf[scala.tools.util.PathResolver$Defaults$]
  //println("Defaults jbc " + defaults.javaBootClassPath)
  //println("Use java cp " + pr.Calculated.useJavaClassPath)
  //println("Settings jbc " + settings.javabootclasspath)
  //println("Defaults sucp " + defaults.scalaUserClassPath)

        val m = pathResolverClass.getMethod("Calculated") 
        val calculated = m.invoke(pr)
        //println("Calculated = " + calculated)
        //println(pr.Calculated)
        val globalClass = loader.loadClass("scala.tools.nsc.Global")
        val globalConstructor = globalClass.getConstructor(settingsClass)
        val global = globalConstructor.newInstance(settings).asInstanceOf[AnyRef]
        val runClass = globalClass.getDeclaredClasses.find(_.getName.endsWith("Run")).get
        val runConstructor = runClass.getDeclaredConstructor(globalClass)
        println(runConstructor)
        val run = runConstructor.newInstance(global)
        //val run = runClass.getDeclaredConstructor(Array[Class[_]](globalClass)).newInstance(global)
        //new global.Run() compile List(srcFile.getPath)
        //assert(global.reporter.hasErrors)



        
      }
    })
    t.setContextClassLoader(loader)
    t.start
    t.join
  }
  //test("2.9 feature does not compile with 2.8 libs"){
    //
    //val scalaAndJavaLibs = System.getProperty("sun.boot.class.path")
    //val javaLibs=scalaAndJavaLibs.split(":").filterNot(_.contains("scala")).mkString(":")
    //val scalaJars = jars_2_8
    //val javaJars = javaLibs.split(":").map(new File(_)).toList
    //
    ////val loader = new URLClassLoader((scalaJars ::: javaJars).map(_.toURI.toURL).toArray, null)
    //val loader = new MyClassLoader((scalaJars.toList ::: javaJars).map(_.toURI.toURL).toArray)
    //val t = new Thread(new Runnable(){
        //def run(){
          //
          //
          //val settingsClass = loader.loadClass("scala.tools.nsc.Settings")
          //val settings = settingsClass.newInstance//.asInstanceOf[Settings]
          //println("******* Default settings ")
          //println(settings.javabootclasspath.value)
          // 
          //
          //
          //settings.javabootclasspath.value = javaLibs
          //settings.usejavacp.value = false
          //settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(new File(rootDir, "out"))))
          //println("Original classpath value " + settings.classpath.value)
          ////settings.classpath.value=scalaJars.map(_.getPath).mkString(":")
          //val newClasspath=settings.classpath.value.toString.split(":").filter{
            //jar => 
            //jar.contains("compiler") || jar.contains("scala-lib")
            //}.mkString(":")
            ////settings.classpath.value=settings.classpath.value.toString
            //settings.classpath.value=newClasspath
            //println("new classpath value " + settings.classpath.value)
            //val scalaLibs=scalaJars.map(_.getPath).mkString(":")
            //settings.classpath.value=scalaLibs
            //settings.javabootclasspath.value = javaLibs + ":" + scalaLibs
            //settings.usejavacp.value = true
            //
            //println("new new classpath value " + settings.classpath.value)
            //
            //
            //val srcFile = makeSrcFile
            //val systemClass = loader.loadClass("java.lang.System")
            //val setPropertyMethod = systemClass.getMethod("setProperty", classOf[String], classOf[String])
            //val getPropertyMethod = systemClass.getMethod("getProperty", classOf[String])
            //setPropertyMethod.invoke(null, "scala.usejavacp", "false")
            //setPropertyMethod.invoke(null, "sun.boot.class.path", javaLibs)
            //setPropertyMethod.invoke(null, "java.class.path", javaLibs + ":" + scalaLibs)
            //setPropertyMethod.invoke(null, "scala.home","/usr/local/scala-2.8.1.final")
            //val pathResolverClass = loader.loadClass("scala.tools.util.PathResolver")
            //val pathResolverConstructor = pathResolverClass.getConstructor(settingsClass)
            //val pr = pathResolverConstructor.newInstance(settings).asInstanceOf[PathResolver]
            //println("System prop " + System.getProperty("scala.home"))
          //println("system prop " + getPropertyMethod.invoke(null, "scala.home"))
        //
        //val pathResolverObjectClass = loader.loadClass("scala.tools.util.PathResolver$")
        //val prObjectConstructors = pathResolverObjectClass.getDeclaredConstructors
        //prObjectConstructors(0).setAccessible(true)
        //val prObject = prObjectConstructors(0).newInstance().asInstanceOf[scala.tools.util.PathResolver$]
        //
        //val defaultsObjectClass = loader.loadClass("scala.tools.util.PathResolver$Defaults$")
        //val defaultsObjectConstructors = defaultsObjectClass.getDeclaredConstructors
        //defaultsObjectConstructors(0).setAccessible(true)
        //val defaults = defaultsObjectConstructors(0).newInstance().asInstanceOf[scala.tools.util.PathResolver$Defaults$]
        //println("Defaults jbc " + defaults.javaBootClassPath)
        //println("Use java cp " + pr.Calculated.useJavaClassPath)
        //println("Settings jbc " + settings.javabootclasspath)
        //println("Defaults sucp " + defaults.scalaUserClassPath)
        //
        //println(pr.Calculated)
        //val globalClass = loader.loadClass("scala.tools.nsc.Global")
        //val globalConstructor = globalClass.getConstructor(settingsClass)
        //val global = globalConstructor.newInstance(settings).asInstanceOf[Global]
        //new global.Run() compile List(srcFile.getPath)
        //assert(global.reporter.hasErrors)
        //
        //
        //
        // 
        //}
        //})
      //t.setContextClassLoader(loader)
      //t.start
      //t.join
      //}
      //
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
    val scalaLibs= jars_2_9.map(_.getPath).mkString(":")
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
