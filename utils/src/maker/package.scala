import java.io.File

// makers equivalent of scala's predef, the root package object
package object maker {
  def file(f : String) : File = new File(f)
  def file(f : File, d : String) : File = new File(f, d)

  // utilities
  def time(f : => Unit) = {
    val s = System.currentTimeMillis
    f
    println("Took: " + (System.currentTimeMillis - s) + "ms")
  }
}
