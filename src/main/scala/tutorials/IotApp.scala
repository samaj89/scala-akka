package tutorials

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import tutorials.Device.{ReadTemperature, RespondTemperature}

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

  override def receive = Actor.emptyBehavior
}

object DeviceManager {
  final case class RequestTrackDevice(groupId: String, deviceId: String)
  case object DeviceRegistered
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

  override def receive = {
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