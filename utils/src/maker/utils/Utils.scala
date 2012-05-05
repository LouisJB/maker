package maker.utils

object Utils {
  def fix[A, B](f: (A => B) => (A => B)): A => B = f(fix(f))(_)

  def withNelDefault[T](default : T)(ls : List[T]) : List[T] = ls match {
    case Nil => default :: Nil
    case _ => ls
  }
}
