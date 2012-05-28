package maker.task

import java.util.concurrent.atomic.AtomicReference

object TaskResults{
  val results : AtomicReference[List[BuildResult[AnyRef]]] = new AtomicReference(Nil)

  def addResult(result : BuildResult[AnyRef]){
    results.set(result :: results.get)
  }

  def lastResult = results.get.head

  def clearResults{
    results.set(Nil)
  }


}
