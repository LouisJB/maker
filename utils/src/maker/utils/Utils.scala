package maker.utils

import java.io.File
import os.Command
import os.OsUtils._

object Utils {
  def fix[A, B](f: (A => B) => (A => B)): A => B = f(fix(f))(_)

  def withNelDefault[T](default : T)(ls : List[T]) : List[T] = ls match {
    case Nil => default :: Nil
    case _ => ls
  }

  /** this assumes gnome for linux
   *  can add cmd line view (lynx) and WM detection later
   */
  def openHtmlFile(f : File) = {
    if (isLinux)
      Command("gnome-open", f.getAbsolutePath).execAsync
    else // assume OSX until we want to support other OSes such as windows
      Command("open", f.getAbsolutePath).exec()
    f
  }

  def showImage(f : File) = {
    if (isLinux)
      Command("xdg-open", f.getAbsolutePath).execAsync
    else // assume OSX until we want to support other OSes such as windows
      Command("open", f.getAbsolutePath).exec()
    f
  }
}
