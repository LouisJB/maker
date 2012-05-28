package maker.task

import org.scalatest.FunSuite
import scala.math._

object Primes{
  def naturalNumbers(from: Long) : Stream[Long] = {
    lazy val result: Stream[Long] = Stream.cons(from, result map(_ + 1))
    result
  }

  def primes(): Stream[Long] = {
    lazy val result: Stream[Long] = Stream.cons(2, naturalNumbers(3) filter {
        n => result.takeWhile(p => p * p <= n).forall(n % _ != 0)
    })
    result
  }

  def primesUpTo(n : Long) = primes.takeWhile(_ <= n).toSet

  def primeDivisors(n : Long) = primesUpTo(ceil(sqrt(n)).toLong + 1).filter(n % _ == 0)
}

class DependencyTreeTests extends FunSuite{

  import Primes._

  test("Primes"){
    assert(primesUpTo(20) == Set(2, 3, 5, 7, 11, 13, 17, 19))
  }

  test("Prime divisors"){
    assert(primeDivisors(120) == Set(2, 3, 5))
  }

  test("random filtration"){
    val ps : Array[Long] = primesUpTo(20).toArray
    val rand = new scala.util.Random(12345)
    def randomPrime : Long = ps(rand.nextInt(ps.size))
    def randomNumPrimes = rand.nextInt(6) + 1
    def randomComposite = {
      var n = 1l
      for (_ <- 1 to randomNumPrimes)
        n *= randomPrime
      n
    }

    object Dep{
      def immediateDependencies(dep : Dep) = Primes.primeDivisors(dep.n).map{p : Long ⇒ Dep(dep.n / p)}
    }
    case class Dep(n : Long){
    }
    for (i <- 0 to 10){
      val originalParents = List.fill(3 + rand.nextInt(100))(randomComposite).toSet
      val tree = DependencyTree(
        originalParents.map{c: Long ⇒ Dep(c)},
        Dep.immediateDependencies
      )
      def predicate(dep : Dep) = {dep.n % 2 == 0}
      val filteredTree = tree.filter(predicate)

      assert(filteredTree.all.size < tree.all.size)
      assert(filteredTree.all.forall(tree.all))

      def allProperParentChildRelationships(tree : DependencyTree[Dep]) = tree.tree.flatMap{
        case (p, cs) ⇒ cs.map(p → _)
      }.toSet

      val pcs = allProperParentChildRelationships(tree)
      val filteredPcs = allProperParentChildRelationships(filteredTree)

      assert(filteredPcs.size < pcs.size)
      filteredPcs.forall(pcs)
    }
  }
}

