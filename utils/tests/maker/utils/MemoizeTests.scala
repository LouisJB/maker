package maker.utils

import org.scalatest.FunSuite
import maker.utils._

class MemoizeTests extends FunSuite {

  test("shouldMemoizeWithSameResults") {
    val tries = 1000

    def fac1(n : BigInt) : BigInt = if (n == 0) 1 else n * fac1(n - 1)

    var s = System.currentTimeMillis()
    var r1 : BigInt = 0
    for (i <- tries to 1 by -1) {
      val x = fac1(i)
      r1 += x
    }
    //println("Done 1, " + (System.currentTimeMillis() - s) + "ms, r1 = " + r1)


    def facRec(f: BigInt => BigInt)(n: BigInt): BigInt = {
      if (n == 0) 1
      else n * f(n - 1)
    }
    lazy val fac: BigInt => BigInt = Memoize1(facRec(fac(_)))

    s = System.currentTimeMillis()
    var r2 : BigInt = 0
    for (i <- tries to 1 by -1) {
      val x = fac(i)
      r2 += x
    }
    //println("Done 2, " + (System.currentTimeMillis() - s) + "ms, r2 = " + r2)


    def facRec2(n: BigInt, f: BigInt => BigInt): BigInt = {
      if (n == 0) 1
      else n * f(n - 1)
    }
    val fac2 = Memoize1.Y(facRec2)

    s = System.currentTimeMillis()
    var r3 : BigInt = 0
    for (i <- tries to 1 by -1) {
      val x = fac2(i)
      r3 += x
    }
    //println("Done 3, " + (System.currentTimeMillis() - s) + "ms, r3 = " + r3)
    assert((r1 == r2) && (r2 == r3))
  }
}

