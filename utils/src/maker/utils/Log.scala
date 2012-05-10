package maker.utils

import org.apache.log4j._

object Log extends Log(Logger.getRootLogger) {
  def debug = Log.level = Level.DEBUG
  def info = Log.level = Level.INFO
}

case class Log(logger: Logger) {

  def level = logger.getEffectiveLevel

  def infoWithTime[T](message:String)(f: =>T) = {
    val stopwatch = new Stopwatch()
    val oldThreadName = Thread.currentThread.getName
    try {
      Thread.currentThread.setName(oldThreadName + " > " + message)
      info(message + " Start")
      val result = f;
      println (message + " Complete. Time: " + stopwatch)
      result
    }
    finally {
      Thread.currentThread.setName(oldThreadName)
    }
  }

  def trace(msg: => AnyRef) = logger.trace(msg)

   def trace(msg: => AnyRef, t: => Throwable) = logger.trace(msg, t)

   def assertLog(assertion: Boolean, msg: => String) = if (assertion) logger.assertLog(assertion, msg)

   def debug(msg: => AnyRef) = logger.debug(msg)

   def debug(msg: => AnyRef, t: => Throwable) = logger.debug(msg, t)

   def error(msg: => AnyRef) = logger.error(msg)

   def error(msg: => AnyRef, t: => Throwable) = logger.error(msg, t)

   def fatal(msg: AnyRef) = logger.fatal(msg)

   def fatal(msg: AnyRef, t: Throwable) = logger.fatal(msg, t)

   def level_=(level: Level) = {
    logger.setLevel(level)
    //logger.getAppender("CONSOLE").asInstanceOf[ConsoleAppender].setThreshold(level)
   }

   def withLevel[T](newLevel: Level)(thunk: => T) = {
    val savedLevel = logger.getEffectiveLevel(); 
    level_=(newLevel); 
    val thunkVal = {thunk}; 
    level_=(savedLevel); 
    thunkVal 
  }

   def name = logger.getName

   def info(msg: => AnyRef) = logger.info(msg)

   def info(msg: => AnyRef, t: => Throwable) = logger.info(msg, t)

   def warn(msg: => AnyRef, t: => Throwable) = logger.warn(msg, t)

   def warn(msg: => AnyRef) = logger.warn(msg)
}
