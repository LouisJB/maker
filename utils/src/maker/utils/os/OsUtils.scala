package maker.utils.os

object OsUtils {
  def isLinux = System.getProperty("os.name").toLowerCase.contains("linux")
  def isOSX = System.getProperty("os.name").toLowerCase.contains("os x")
  def isUnix = isLinux || isOSX
}
