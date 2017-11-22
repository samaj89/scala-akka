package tutorials

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import tutorials.DeviceGroup.{ReplyDeviceList, RequestDeviceList}
import tutorials.DeviceManager.{ReplyGroupList, RequestGroupList, RequestTrackDevice}

import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn

/**
  * Code based on the Akka tutorial at:
  * https://doc.akka.io/docs/akka/current/scala/guide/tutorial_2.html
  * https://doc.akka.io/docs/akka/current/scala/guide/tutorial_3.html
  * https://doc.akka.io/docs/akka/current/scala/guide/tutorial_4.html
  * https://doc.akka.io/docs/akka/current/guide/tutorial_5.html?language=scala
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
  def props(): Props = Props(new DeviceManager)

  final case class RequestGroupList(requestId: Long)
  final case class ReplyGroupList(requestId: Long, ids: Set[String])

  final case class RequestTrackDevice(groupId: String, deviceId: String)
  case object DeviceRegistered
}

class DeviceManager extends Actor with ActorLogging {
  var groupIdToActor = Map.empty[String, ActorRef]
  var actorToGroupId = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("DeviceManager started")
  override def postStop(): Unit = log.info("DeviceManager stopped")

  override def receive: Receive = {
    case trackMsg @ RequestTrackDevice(groupId, _) =>
      groupIdToActor.get(groupId) match {
        case Some(ref) =>
          ref forward trackMsg
        case None =>
          log.info("Creating device group actor for {}", groupId)
          val groupActor = context.actorOf(DeviceGroup.props(groupId), "group-" + groupId)
          context.watch(groupActor)
          groupActor forward trackMsg
          groupIdToActor += groupId -> groupActor
          actorToGroupId += groupActor -> groupId
      }

    case RequestGroupList(requestId) =>
      sender() ! ReplyGroupList(requestId, groupIdToActor.keySet)

    case Terminated(groupActor) =>
      val groupId = actorToGroupId(groupActor)
      log.info("Device group actor for {} has been terminated", groupId)
      actorToGroupId -= groupActor
      groupIdToActor -= groupId
  }
}

object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))

  final case class RequestDeviceList(requestId: Long)
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  final case class RequestAllTemperatures(requestId: Long)
  final case class RespondAllTemperatures(requestId: Long, temperatures: Map[String, TemperatureReading])

  sealed trait TemperatureReading
  final case class Temperature(value: Double) extends TemperatureReading
  case object TemperatureNotAvailable extends TemperatureReading
  case object DeviceNotAvailable extends TemperatureReading
  case object DeviceTimedOut extends TemperatureReading
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

    case RequestDeviceList(requestId) =>
      sender() ! ReplyDeviceList(requestId, deviceIdToActor.keySet)

    case Terminated(deviceActor) =>
      val deviceId = actorToDeviceId(deviceActor)
      log.info("Device actor {} has been terminated", deviceId)
      actorToDeviceId -= deviceActor
      deviceIdToActor -= deviceId
  }
}

object DeviceGroupQuery {
  // a message to send in the event that the ReadTemperature request is not responded to in time
  case object CollectionTimeout

  def props(actorToDeviceId: Map[ActorRef, String], requestId: Long,
              requester: ActorRef, timeout: FiniteDuration): Props =
    Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))
}

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