package tutorials

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

/**
  * Code based on the Akka tutorial at https://doc.akka.io/docs/akka/current/scala/guide/tutorial_1.html
  */

class DeviceManagerSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with FlatSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("DeviceSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "A DeviceManager actor" should "be able to list active groups" in {
    val probe = TestProbe()
    val managerActor = system.actorOf(DeviceManager.props())

    managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    managerActor.tell(DeviceManager.RequestGroupList(requestId = 0), probe.ref)
    probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 0, Set("group1", "group2")))
  }
}
