package maker.utils

import scalaz.Scalaz._


object RichString {
	implicit def StringToRichString(s : String) = new RichString(s)

  def box(args: Seq[Any]): Array[AnyRef] = args.flatMap(boxValue).toArray
  def nullsafe(args: Seq[Any]): Seq[Any] = args.map(_ ?? "null")

  private def boxValue(x: Any): Seq[AnyRef] = {
    if (x == null) {
      null
    }
    else x match {
      case x: Boolean => new java.lang.Boolean(x) :: Nil
      case x: Byte => new java.lang.Byte(x) :: Nil
      case x: Short => new java.lang.Short(x) :: Nil
      case x: Char => new java.lang.Character(x) :: Nil
      case x: Int => new java.lang.Integer(x) :: Nil
      case x: Long => new java.lang.Long(x) :: Nil
      case x: Float => new java.lang.Float(x) :: Nil
      case x: Double => new java.lang.Double(x) :: Nil
      // TODO: [17 Jan 2012]: Replace with generic implementation x.productIterator.toSeq.flatMap(boxValue)
      case x: (Any, Any) => boxValue(x._1) ++ boxValue(x._2)
      case x: Unit => "()" :: Nil
      case x: AnyRef => x :: Nil
    }
  }

	class RichString(s: String){
		def % (args: Any*) = String.format(s, box(nullsafe(args)):_*)
	}
}
