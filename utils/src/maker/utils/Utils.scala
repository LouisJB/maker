package maker.utils

object Utils {

  // basic fixpoint Y combinator as we do a lot of recursion and doing it anonymously is nicer
  def fix[A, B](f: (A => B) => (A => B)): A => B = f(fix(f))(_)
}
