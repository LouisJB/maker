/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package maker.remoteakka

import akka.actor.Actor


class AdvancedCalculatorActor extends Actor {
  println("Advanced calc created")
  def receive = {
    case ProcessID(id) ⇒
      println("Advanced calc actor Received " + id)
      sender ! ProcessID()
  }
}
