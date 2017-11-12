package greetings

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import greetings.AkkaGreeters.Greeter.{Greet, WhoToGreet}
import greetings.AkkaGreeters.Printer.Greeting

/**
  * A basic Akka example involving passing greetings
  */

object AkkaGreeters {

  class Greeter(message: String, printerActor: ActorRef) extends Actor {
    var greeting = ""

    def receive: Receive = {
      case WhoToGreet(who) => greeting = s"$message, $who"
      case Greet => printerActor ! Greeting(greeting)
    }
  }

  // Good practice to put an actor's associated messages and a props method in its companion object
  object Greeter {
    def props(message: String, printerActor: ActorRef): Props = Props(new Greeter(message, printerActor))
    final case class WhoToGreet(who: String)
    case object Greet
  }


  class Printer extends Actor with ActorLogging {
    def receive = {
      case Greeting(greeting) => log.info(s"${sender()} sends their greetings: $greeting")
    }
  }

  object Printer {
    def props: Props = Props[Printer]
    final case class Greeting(greeting: String)
  }

}
