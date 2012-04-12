/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package maker.remoteakka.creation

/*
 * comments like //#<tag> are there for inclusion into docs, please don’t remove
 */

import akka.kernel.Bootable
import com.typesafe.config.ConfigFactory
import scala.util.Random
import akka.actor._
import maker.remoteakka._

class CreationApplication extends Bootable {
  //#setup
  val system = ActorSystem("RemoteCreation", ConfigFactory.load.getConfig("remotecreation"))
  val localActor = system.actorOf(Props[CreationActor], "creationActor")
  val remoteActor = system.actorOf(Props[AdvancedCalculatorActor], "advancedCalculator")

  def doSomething(op: AnyRef) = {
    localActor ! (remoteActor, op)
  }
  //#setup

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}

//#actor
class CreationActor extends Actor {
  def receive = {
    case (actor: ActorRef, op: AnyRef) ⇒ actor ! op
    case result: MathResult ⇒ result match {
      case MultiplicationResult(n1, n2, r) ⇒ println("Mul result: %d * %d = %d".format(n1, n2, r))
      case DivisionResult(n1, n2, r)       ⇒ println("Div result: %.0f / %d = %.2f".format(n1, n2, r))
    }
    case ProcessID(id) ⇒ println("Creation actor Received " + id + " from remote actor, this actor's id is " + ProcessID())
  }
}
//#actor

object CreationApp {
  def main(args: Array[String]) {
    val app = new CreationApplication
    println("Started Creation Application")
    while (true) {
      app.doSomething(ProcessID())
      //if (Random.nextInt(100) % 2 == 0) app.doSomething(Multiply(Random.nextInt(20), Random.nextInt(20)))
      //else app.doSomething(Divide(Random.nextInt(10000), (Random.nextInt(99) + 1)))

      Thread.sleep(200)
    }
  }
}
