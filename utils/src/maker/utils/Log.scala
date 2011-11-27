package maker.utils

import org.apache.log4j._

import util.DynamicVariable
import scalaz.Scalaz._

case class RichLevel(level : Level) extends Ordered[Level] {
  // Log4J levels have smaller numbers for higher levels. Reverse this here
  def compare(rhs : Level) = if (level.toInt < rhs.toInt) 
    1 
  else if (level.toInt > rhs.toInt) 
    -1
  else
    0
}
object RichLevel{
  implicit def toRichLevel(level : Level) = new RichLevel(level)
}

class AdaptingLogger(val rootLogger: VarLogger) extends VarLogger {
  override def name = rootLogger.name
  override def trace(msg: => AnyRef) = rootLogger.trace(msg)
  override def trace(msg: => AnyRef, t: => Throwable) = rootLogger.trace(msg, t)
  override def assertLog(assertion: Boolean, msg: => String) = rootLogger.assertLog(assertion, msg)
  override def isEnabledFor(level: Level) = rootLogger.isEnabledFor(level)
  def isDebugEnabled = isEnabledFor(Level.DEBUG)
  override def debug(msg: => AnyRef) = rootLogger.debug(msg)
  override def debug(msg: => AnyRef, t: => Throwable) = rootLogger.debug(msg, t)
  override def error(msg: => AnyRef) = rootLogger.error(msg)
  override def error(msg: => AnyRef, t: => Throwable) = rootLogger.error(msg, t)
  override def fatal(msg: AnyRef) = rootLogger.fatal(msg)
  override def fatal(msg: AnyRef, t: Throwable) = rootLogger.fatal(msg, t)
  override def level = rootLogger.level
  override def level_=(level: Level) = rootLogger.level = level
  override def level[T](newLevel: Level)(thunk: => T) = rootLogger.level(newLevel)(thunk)
  override def info(msg: => AnyRef) = rootLogger.info(msg)
  override def info(msg: => AnyRef, t: => Throwable) = rootLogger.info(msg, t)
  override def warn(msg: => AnyRef) = rootLogger.warn(msg)
  override def warn(msg: => AnyRef, t: => Throwable) = rootLogger.warn(msg, t)
}

/**
 * A thin wrapper around log4j.
 */
object Log extends ExtendedLog(Log4JLogger.logger) {
  def forName(name: String)     = new ExtendedLog(Log4JLogger.forName(name))
  def forClass[T: Manifest]     = new ExtendedLog(Log4JLogger.forClass(implicitly[Manifest[T]].erasure))
}

trait Log {
  lazy val log: ExtendedLog = new ExtendedLog(Log4JLogger.forClass(getClass))
}

class ExtendedLog(adapted: VarLogger) extends AdaptingLogger(adapted) {
  def infoWithTime[T](message:String)(f: =>T) = {
    val stopwatch = new Stopwatch()
    val oldThreadName = Thread.currentThread.getName
    try {
      Thread.currentThread.setName(oldThreadName + " > " + message)
      info(message + " Start")
      val result = f;
      println (message + " Complete. Time: " + stopwatch)
      result
    } finally {
      Thread.currentThread.setName(oldThreadName)
    }
  }
  def infoWithTimeGapTop[T](message:String)(f: =>T) = {
    println("")
    println("")
    infoWithTime(message){f}
  }
  def infoWithTimeGapBottom[T](message:String)(f: =>T) = {
    val r = infoWithTime(message){f}
    println("")
    println("")
    r
  }

  def infoF[T](msg: => AnyRef)(f: => T): T                   = {info(msg); f}
  def infoF[T](msg: => AnyRef, t: => Throwable)(f: => T): T  = {info(msg, t); f}
  def warnF[T](msg: => AnyRef)(f: => T): T                   = {warn(msg); f}
  def warnF[T](msg: => AnyRef, t: => Throwable)(f: => T): T  = {warn(msg, t); f}
  def errorF[T](msg: => AnyRef)(f: => T): T                  = {error(msg); f}
  def errorF[T](msg: => AnyRef, t: => Throwable)(f: => T): T = {error(msg, t); f}
  def never(msg: => AnyRef) {}
  def neverF(msg: => AnyRef) {}
  def never(msg: => AnyRef, t: => Throwable) {}
}

trait VarLogger {
  def trace(msg: => AnyRef): Unit = ()

  def trace(msg: => AnyRef, t: => Throwable): Unit = ()

  def assertLog(assertion: Boolean, msg: => String): Unit = ()

  def debug(msg: => AnyRef): Unit = ()

  def debug(msg: => AnyRef, t: => Throwable): Unit = ()

  def error(msg: => AnyRef): Unit = ()

  def error(msg: => AnyRef, t: => Throwable): Unit = ()

  def fatal(msg: AnyRef): Unit = ()

  def fatal(msg: AnyRef, t: Throwable): Unit = ()

  def level: Level = Level.OFF

  def level_=(level: Level): Unit = ()

  def level[T](newLevel: Level)(thunk: => T): T = thunk
  def off[T](thunk: => T): T = level(Level.OFF) { thunk }

  def name: String = "Null"

  def info(msg: => AnyRef): Unit = ()

  def info(msg: => AnyRef, t: => Throwable): Unit = ()

  def isEnabledFor(level: Level): Boolean = false

  def warn(msg: => AnyRef): Unit = ()

  def warn(msg: => AnyRef, t: => Throwable): Unit = ()
}


object Log4JLogger {
  System.setProperty("log4j.configuration", "log4j.properties")

  lazy val logger = new Log4JLogger(Logger.getRootLogger, levelTransformer)
  def forName(name: String) = new Log4JLogger(Logger.getLogger(name), levelTransformer)
  def forClass(clazz: Class[_]) = new Log4JLogger(Logger.getLogger(clazz), levelTransformer)

  private val levelTransformer = new DynamicVariable[Level](Level.OFF)
}


class Log4JLogger(val logger: Logger, levelTransformer: DynamicVariable[Level]) extends VarLogger {
  import RichLevel._
  override def trace(msg: => AnyRef) = if (isEnabledFor(Level.TRACE)) logger.trace(msg)

  override def trace(msg: => AnyRef, t: => Throwable) = if (isEnabledFor(Level.TRACE)) logger.trace(msg, t)

  override def assertLog(assertion: Boolean, msg: => String) = if (assertion) logger.assertLog(assertion, msg)

  override def debug(msg: => AnyRef) = if (isEnabledFor(Level.DEBUG)) logger.debug(msg)

  override def debug(msg: => AnyRef, t: => Throwable) = if (isEnabledFor(Level.DEBUG)) logger.debug(msg, t)

  override def error(msg: => AnyRef) = if (isEnabledFor(Level.ERROR)) logger.error(msg)

  override def error(msg: => AnyRef, t: => Throwable) = if (isEnabledFor(Level.ERROR)) logger.error(msg, t)

  override def fatal(msg: AnyRef) = logger.fatal(msg)

  override def fatal(msg: AnyRef, t: Throwable) = logger.fatal(msg, t)

  private def getInheritedLevel = {
    def recurse(category: Category): Level = {
      category.getLevel ?? recurse(category.getParent)
    }

    recurse(logger)
  }

  override def level = levelTransformer.value

  override def isEnabledFor(level: Level): Boolean = this.level >= level

  override def level_=(level: Level) = logger.setLevel(level)

  override def level[T](newLevel: Level)(thunk: => T) = levelTransformer.withValue(newLevel) {val savedLevel = logger.getEffectiveLevel(); logger.setLevel(Level.ALL); val thunkVal = {thunk}; logger.setLevel(savedLevel); thunkVal }

  override def name = logger.getName

  override def info(msg: => AnyRef) = if (isEnabledFor(Level.INFO)) logger.info(msg)

  override def info(msg: => AnyRef, t: => Throwable) = if (isEnabledFor(Level.INFO)) logger.info(msg, t)

  def isEnabledFor(level: Priority) = logger.isEnabledFor(level)

  override def warn(msg: => AnyRef, t: => Throwable) = if (isEnabledFor(Level.WARN)) logger.warn(msg, t)
}


