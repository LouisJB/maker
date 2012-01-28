package maker.utils.os

object OsUtils {
  def isLinux = System.getProperty("os.name").toLowerCase.contains("linux")
}
