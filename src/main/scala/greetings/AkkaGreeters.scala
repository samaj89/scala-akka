package greetings

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import greetings.AkkaGreeters.Greeter._
import greetings.AkkaGreeters.{Greeter, Printer}
import greetings.AkkaGreeters.Printer._

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

object GreetersMain extends App {

  val system: ActorSystem = ActorSystem("greetAkka")

  val printer: ActorRef = system.actorOf(Printer.props)

  val englishGreeter: ActorRef =
    system.actorOf(Greeter.props("Good morning", printer), "englishGreeter")
  val frenchGreeter: ActorRef =
    system.actorOf(Greeter.props("Bonjour", printer), "frenchGreeter")
  val germanGreeter: ActorRef =
    system.actorOf(Greeter.props("Guten Morgen", printer), "germanGreeter")
  val russianGreeter: ActorRef =
    system.actorOf(Greeter.props("Dobroe utro", printer), "russianGreeter")

  englishGreeter ! WhoToGreet("Mr Smith")
  englishGreeter ! Greet

  frenchGreeter ! WhoToGreet("mademoiselle")
  frenchGreeter ! Greet

  germanGreeter ! WhoToGreet("Frau Oebinger")
  germanGreeter ! Greet

  russianGreeter ! WhoToGreet("Alyosha")
  russianGreeter ! Greet

  system.terminate()

}
