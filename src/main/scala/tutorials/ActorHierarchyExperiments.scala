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

object ActorHierarchyExperiments extends App {
  val system = ActorSystem("testSystem")

//  val firstRef = system.actorOf(Props[PrintMyActorRefActor], "first-Actor")
//  println(s"First: $firstRef")
//  firstRef ! "printit"

  val first = system.actorOf(Props[StartStopActor1], "first")
  first ! "stop"

  println(">>> Press ENTER to exit <<<")
  try StdIn.readLine()
  finally system.terminate()
}
