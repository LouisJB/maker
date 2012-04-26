package maker.utils

class Memoize1[-T, +R](f: T => R) extends (T => R) {
  import scala.collection.mutable
  private[this] val vals = mutable.Map.empty[T, R]

  def apply(x: T): R = {
    if (vals.contains(x))
      vals(x)
    else {
      val y = f(x)
      vals += (x -> y)
      y
    }
  }
}

object Memoize1 {
  def apply[T, R](f: T => R) = new Memoize1(f)

  def Y[T, R](f: (T, T => R) => R) = {
    lazy val yf : T => R = Memoize1(f(_, yf(_)))
    yf
  }
}

object Y {
  def apply[T, R](f: (T, T => R) => R): (T => R) = {
    lazy val yf : T => R = f(_, yf(_))
    yf
  }
}


case class Memoize[A, B](fn : A => B){
  private var cache : Map[A, B] = Map.empty[A, B]
  def apply(a : A) : B = {
    if (! cache.contains(a))
      cache += a -> fn(a)
    cache(a)
  }
  def size = cache.size
}
case class Memoize2[A, B, C](fn : (A, B) => C){
  private var cache : Map[(A, B), C] = Map.empty[(A, B), C]
  def apply(a : A, b : B) : C = {
    if (! cache.contains((a, b)))
      cache += (a, b) -> fn(a, b)
    cache((a, b))
  }
  def size = cache.size
}

object Memoize {
  def memoize[A, B](fn: A => B) = Memoize(fn)
  def memoize2[A, B, C](fn: (A, B) => C) = Memoize2(fn)
}

object TestMemo {

  def main(args : Array[String]) {

    val max = 1000

    def fac1(n : BigInt) : BigInt = if (n == 0) 1 else n * fac1(n - 1)

    var s = System.currentTimeMillis()
    var r : BigInt = 0
    for (i <- max to 1 by -1) {
      val x = fac1(i)
      r += x
      //println(fac1(i))
    }
    println("Done 1, " + (System.currentTimeMillis() - s) + ", r = " + r)


    def facRec(f: BigInt => BigInt)(n: BigInt): BigInt = {
      if (n == 0) 1
      else n * f(n - 1)
    }
    lazy val fac: BigInt => BigInt = Memoize1(facRec(fac(_)))

    s = System.currentTimeMillis()
    r = 0
    for (i <- max to 1 by -1) {
      val x = fac(i)
      r += x
      //println(fac(i))
    }
    println("Done 2, " + (System.currentTimeMillis() - s) + ", r = " + r)


    def facRec2(n: BigInt, f: BigInt => BigInt): BigInt = {
      if (n == 0) 1
      else n * f(n - 1)
    }
    val fac2 = Memoize1.Y(facRec2)

    s = System.currentTimeMillis()
    r = 0
    for (i <- max to 1 by -1) {
      val x = fac2(i)
      r += x
      //println(fac(i))
    }
    println("Done 3, " + (System.currentTimeMillis() - s) + ", r = " + r)
  }
}
