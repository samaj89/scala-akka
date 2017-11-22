package tutorials

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import scala.concurrent.duration.FiniteDuration

/**
  * Code based on the Akka tutorial at https://doc.akka.io/docs/akka/current/scala/guide/tutorial_1.html
  */

class DeviceGroupQuery(actorToDeviceId: Map[ActorRef, String], requestId: Long,
                       requester: ActorRef, timeout: FiniteDuration) extends Actor with ActorLogging {

  import DeviceGroupQuery._
  import context.dispatcher
  // used to send the CollectionTimeout message if response not received in time; Cancellable if response received
  val queryTimeoutTimer = context.system.scheduler.scheduleOnce(timeout, self, CollectionTimeout)

  // when DeviceGroupQuery actor created, watch all Device actors and send each a ReadTemperature message
  override def preStart(): Unit = {
    actorToDeviceId.keysIterator.foreach { deviceActor ⇒
      context.watch(deviceActor)
      deviceActor ! Device.ReadTemperature(0)
    }
  }

  override def postStop(): Unit = queryTimeoutTimer.cancel()

  override def receive: Receive =
    waitingForReplies(
      Map.empty,
      actorToDeviceId.keySet
    )

  def waitingForReplies(repliesSoFar: Map[String, DeviceGroup.TemperatureReading],
                        stillWaiting: Set[ActorRef]): Receive = {
    case Device.RespondTemperature(0, valueOption) =>
      val deviceActor = sender()
      val reading = valueOption match {
        case Some(value) => DeviceGroup.Temperature(value)
        case None        => DeviceGroup.TemperatureNotAvailable
      }
      receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar)

    case Terminated(deviceActor) =>
      receivedResponse(deviceActor, DeviceGroup.DeviceNotAvailable, stillWaiting, repliesSoFar)

    case CollectionTimeout =>
      val timedOutReplies =
        stillWaiting.map { deviceActor =>
          val deviceId = actorToDeviceId(deviceActor)
          deviceId -> DeviceGroup.DeviceTimedOut
        }
      requester ! DeviceGroup.RespondAllTemperatures(requestId, repliesSoFar ++ timedOutReplies)
      context.stop(self)
  }

  def receivedResponse(deviceActor: ActorRef, reading: DeviceGroup.TemperatureReading,
                       stillWaiting: Set[ActorRef], repliesSoFar: Map[String, DeviceGroup.TemperatureReading]): Unit = {

    context.unwatch(deviceActor)
    val deviceId = actorToDeviceId(deviceActor)
    val newStillWaiting = stillWaiting - deviceActor

    val newRepliesSoFar = repliesSoFar + (deviceId -> reading)
    if (newStillWaiting.isEmpty) {
      requester ! DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar)
      context.stop(self)
    } else {
      context.become(waitingForReplies(newRepliesSoFar, newStillWaiting))
    }
  }
}

object DeviceGroupQuery {
  // a message to send in the event that the ReadTemperature request is not responded to in time
  case object CollectionTimeout

  def props(actorToDeviceId: Map[ActorRef, String], requestId: Long,
            requester: ActorRef, timeout: FiniteDuration): Props =
    Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))
}