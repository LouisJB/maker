package maker.utils

object Utils {
  def fix[A, B](f: (A => B) => (A => B)): A => B = f(fix(f))(_)
}
