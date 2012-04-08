package maker
import org.scalatest.FunSuite
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.{Props => AkkaProps}
import akka.routing.SmallestMailboxRouter
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import akka.dispatch.Await
import akka.util.Duration
import maker.utils.Log

case class MyMessage(x : Int)
case object Done
case object Go
case class TotalSleep(x : Double)

class MyActor() extends Actor{
  def receive = {
    case MyMessage(x) => {
      Thread.sleep(x)
      sender ! Done
    }
  }
}

case class Counter(router : ActorRef, numCalls : Int, maxWait : Int) extends Actor{
  var callsReceived = 0
  var originalCaller :ActorRef = _
  var totalSleep : Int = 0
  def receive = {
    case Go => 
      originalCaller = sender
      for (i <- 0 until numCalls){
        val sleep = (math.random * maxWait).asInstanceOf[Int]
        totalSleep += sleep
        router ! MyMessage(sleep)
      }
    case Done => {
      callsReceived += 1
      println("" + callsReceived + " " + (numCalls == callsReceived))
      if (callsReceived == numCalls){
        println("Sending done")
        originalCaller ! TotalSleep(totalSleep)
      }
    }
  }
}

class AkkaTests extends FunSuite{
  test("can use application conf"){
    val system = ActorSystem("conf")
    val numSlaves = 20
    val numCalls = 1000
    val maxWait = 100
    val router = system.actorOf(AkkaProps[MyActor].withRouter(SmallestMailboxRouter(nrOfInstances=numSlaves)).withDispatcher("my-dispatcher"), name = "fred")
    val counter = system.actorOf(AkkaProps(Counter(router, numCalls, maxWait)))
    implicit val timeout = Timeout(1000000)
    Log.infoWithTime("Timing"){
      val future = counter ? Go 
      Await.result(future, Duration.Inf) match {
        case TotalSleep(s) => {
          println("total = " + s)
          println("per slave = " + (s / numSlaves))
        }
      }
    }
      
    
  }
}
