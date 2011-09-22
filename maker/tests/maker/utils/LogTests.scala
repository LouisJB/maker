package maker.utils

import org.scalatest.FunSuite
import org.apache.log4j._

class LogTests extends FunSuite {
  import Level._
  import RichLevel._
  val levels = List(ALL, TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF)

  test("moreInclusiveLoggingLevelsShouldBeGreaterThanLessInclusiveLevels") {
    (levels zip (levels.tail)).foreach { case (moreInclusive, lessInclusive) =>
      assert(moreInclusive > lessInclusive)
    }
  }

  test("shouldBeAbleToSetLoggingLevelDynamically") {
    val currentLevel = Log.level

    levels.foreach{
      level =>
        assert(Log.level == currentLevel)
        Log.level(level){
          assert(Log.level === level)
        }
    }
  }
}
