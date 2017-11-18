package tutorials

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import tutorials.DeviceManager.RequestTrackDevice

import scala.io.StdIn

/**
  * Code based on the Akka tutorial at https://doc.akka.io/docs/akka/current/scala/guide/tutorial_2.html
  * and at https://doc.akka.io/docs/akka/current/scala/guide/tutorial_3.html
  */

object IotSupervisor {
  def props(): Props = Props(new IotSupervisor)
}

class IotSupervisor extends Actor with ActorLogging {
  override def preStart(): Unit = log.info("IoT Application started")
  override def postStop(): Unit = log.info("IoT Application stopped")

  override def receive: Receive = Actor.emptyBehavior
}

object DeviceManager {
  final case class RequestTrackDevice(groupId: String, deviceId: String)
  case object DeviceRegistered
}

object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))
}

class DeviceGroup(groupId: String) extends Actor with ActorLogging {
  var deviceIdToActor = Map.empty[String, ActorRef]
  var actorToDeviceId = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("DeviceGroup {} started", groupId)
  override def postStop(): Unit = log.info("DeviceGroup {} stopped", groupId)

  override def receive: Receive = {
    // Handling a registration request with a groupId matching that of the DeviceGroup
    case trackMsg @ RequestTrackDevice(`groupId`, _) =>
      deviceIdToActor.get(trackMsg.deviceId) match {
        // If the DeviceGroup has a child with the required DeviceId, forward request...
        case Some(deviceActor) =>
          deviceActor forward trackMsg
        // ...if not, create the required Device actor and forward request
        case None =>
          log.info("Creating device actor for {}", trackMsg.deviceId)
          val deviceActor = context.actorOf(Device.props(groupId, trackMsg.deviceId), s"device-${trackMsg.deviceId}")
          // watch newly created actor and receive Terminated message if it stops
          context.watch(deviceActor)
          deviceIdToActor += trackMsg.deviceId -> deviceActor
          actorToDeviceId += deviceActor -> trackMsg.deviceId
          deviceActor forward trackMsg
      }

    case RequestTrackDevice(groupId, deviceId) =>
      log.warning(
        "Ignoring TrackDevide request for {}. This actor is responsible for ().",
        groupId, this.groupId
      )

    case Terminated(deviceActor) =>
      val deviceId = actorToDeviceId(deviceActor)
      log.info("Device actor {} has been terminated", deviceId)
      actorToDeviceId -= deviceActor
      deviceIdToActor -= deviceId
  }
}

object Device {
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  final case class RecordTemperature(requestId: Long, value: Double)
  final case class TemperatureRecorded(requestId: Long)

  final case class ReadTemperature(requestId: Long)
  final case class RespondTemperature(requestId: Long, value: Option[Double])
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._
  var lastTemperatureReading: Option[Double] = None

  override def preStart(): Unit = log.info("Device actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit = log.info("Device actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = {
    case DeviceManager.RequestTrackDevice(`groupId`, `deviceId`) =>
      sender() ! DeviceManager.DeviceRegistered

    case DeviceManager.RequestTrackDevice(groupId, deviceId) =>
      log.info("Ignoring TrackDevice request for {}-{}. This actor is responsible for {}-{}.",
        groupId, deviceId, this.groupId, this.deviceId)

    case RecordTemperature(id, value) =>
      log.info("Recorded temperature reading {} with {}", value, id)
      lastTemperatureReading = Some(value)
      sender() ! TemperatureRecorded(id)

    case ReadTemperature(id) =>
      sender() ! RespondTemperature(id, lastTemperatureReading)
  }
}

object IotApp {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("iot-system")

    try {
      val supervisor = system.actorOf(IotSupervisor.props(), "iot-supervisor")
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }
}