package maker.project
import org.scalatest.FunSuite
import java.io.File
import maker.utils.FileUtils._
import org.scalatest.BeforeAndAfterEach

class ProjectTests extends FunSuite with BeforeAndAfterEach{

  val root = tempDir("fred")
  var proj : Project = _
  val fooSrc = new File(root, "src/foo/Foo.scala")
  val barSrc = new File(root, "src/foo/bar/Bar.scala")

  def writeStandardProject{
    proj = Project("foox", root, List(new File(root, "src")), Nil, new File(root, "out"), new File(root, "out-jar"))

    writeToFile(
      fooSrc,
      """
      package foo
      case class Foo(x : Double){
      }
      """
    )
    writeToFile(
      barSrc,
      """
      package foo.bar
      import foo.Foo

      case class Bar(x : Foo){
      }
      """
    )
  }

  override def afterEach(){
    proj.delete
  }

  //test("Compilation makes class files"){
    //writeStandardProject
    //proj.clean
    //assert(proj.classFiles.size === 0)
    //proj.compile
    //assert(proj.classFiles.size > 0)
    //proj.clean
    //assert(proj.classFiles.size === 0)
    //}
    //
    //test("Package makes jar"){
      //writeStandardProject
      //proj.clean
      //assert(!proj.outputJar.exists)
      //proj.pack
      //assert(proj.outputJar.exists)
      //proj.clean
      //assert(!proj.outputJar.exists)
      //proj.delete
      //}
System
  test("Compilation not done if signature unchanged"){
    writeStandardProject
    val fooScala = new File(proj.root, "src/foo/Foo.scala")
    proj.compile
    val fooClass = new File(proj.outputDir, "foo/Foo.class")
    val barClass = new File(proj.outputDir, "foo/bar/Bar.class")
    assert(barClass.exists)
    assert(fooClass.exists)
    val fooScalaTimestamp = fooScala.lastModified
    val fooClassTimestamp = fooClass.lastModified
    val barClassTimestamp = barClass.lastModified
    proj.compileRequired
    Thread.sleep(1000)
    
    fooSrc.delete
    writeToFile(
      fooSrc,
      """
      package foo
      case class Foo(x : Double){
      }
      """
    )
    proj.compile
    assert(fooClass.lastModified > fooClassTimestamp)
  }
}
