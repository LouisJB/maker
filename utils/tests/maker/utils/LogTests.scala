package maker.utils

import org.scalatest.FunSuite
import org.apache.log4j._

class LogTests extends FunSuite {
  import Level._
  val levels = List(ALL, TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF)

  test("shouldBeAbleToSetLoggingLevelDynamically") {
    val currentLevel = Log.level

    levels.foreach{
      level =>
      assert(Log.level == currentLevel)
      Log.withLevel(level){
        assert(Log.level === level)
      }
    }
  }
}
