package tutorials

import akka.actor.{Actor, ActorSystem, Props}

import scala.io.StdIn

/**
  * Code baseed on the Akka tutorial at https://doc.akka.io/docs/akka/current/scala/guide/tutorial_1.html
  */

class PrintMyActorRefActor extends Actor {
  def receive = {
    case "printit" =>
      val secondRef = context.actorOf(Props.empty, "second-actor")
      println(s"Second: $secondRef")
  }
}

class StartStopActor1 extends Actor {
  override def preStart(): Unit = {
    println("first started")
    context.actorOf(Props[StartStopActor2], "second")
  }

  override def postStop(): Unit = println("first stopped")

  def receive = {
    case "stop" => context.stop(self)
  }
}

class StartStopActor2 extends Actor {
  override def preStart(): Unit = println("second started")
  override def postStop(): Unit = println("second stopped")
  def receive = Actor.emptyBehavior
}

class SupervisingActor extends Actor {
  val child = context.actorOf(Props[SupervisedActor], "supervised-actor")

  def receive = {
    case "failChild" => child ! "fail"
  }
}

class SupervisedActor extends Actor {
  override def preStart(): Unit = println("supervised actor started")
  override def postStop(): Unit = println("supervised actor stopped")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = println("preparing to restart supervised actor")
  override def postRestart(reason: Throwable): Unit = println("supervised actor restarted")

  def receive = {
    case "fail" =>
      println("supervised actor fails now")
      throw new Exception("I failed!")
  }
}

object ActorHierarchyExperiments extends App {
  val system = ActorSystem("testSystem")

//  val firstRef = system.actorOf(Props[PrintMyActorRefActor], "first-Actor")
//  println(s"First: $firstRef")
//  firstRef ! "printit"

//  val first = system.actorOf(Props[StartStopActor1], "first")
//  first ! "stop"

  val supervisingActor = system.actorOf(Props[SupervisingActor], "supervising-actor")
  supervisingActor ! "failChild"

  println(">>> Press ENTER to exit <<<")
  try StdIn.readLine()
  finally system.terminate()
}
