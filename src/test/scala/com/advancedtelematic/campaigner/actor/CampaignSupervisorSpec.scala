package com.advancedtelematic.campaigner.actor

import akka.testkit.TestProbe
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.db.{Campaigns, UpdateSupport}
import com.advancedtelematic.campaigner.util.{ActorSpec, CampaignerSpec}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import scala.concurrent.duration._

class CampaignSupervisorSpec extends ActorSpec[CampaignSupervisor] with CampaignerSpec with UpdateSupport {

  import CampaignScheduler._
  import CampaignSupervisor._

  val campaigns = Campaigns()

  def buildCampaignWithUpdate: Campaign = {
    val update = genMultiTargetUpdate.generate
    val updateId = updateRepo.persist(update).futureValue
    arbitrary[Campaign].generate.copy(updateId = updateId)
  }

  "campaign supervisor" should "pick up unfinished and fresh campaigns" in {
    val partiallyScheduledCampaign = buildCampaignWithUpdate
    val freshCampaign = buildCampaignWithUpdate
    val scheduledCampaign = buildCampaignWithUpdate
    val parent = TestProbe()
    val n = Gen.choose(batch, batch * 2).generate

    val scheduledCampaignDevices = Gen.listOfN(n, genDeviceId).generate.toSet
    campaigns.create(
      scheduledCampaign,
      Set.empty,
      scheduledCampaignDevices,
      Seq.empty).futureValue
    campaigns.scheduleDevices(
      scheduledCampaign.id,
      scheduledCampaignDevices.toSeq).futureValue

    val partiallyScheduledCampaignDevices = Gen.listOfN(n, genDeviceId).generate.toSet
    val nScheduled = Gen.choose(1, n - 1).generate
    campaigns.create(
      partiallyScheduledCampaign,
      Set.empty,
      partiallyScheduledCampaignDevices,
      Seq.empty).futureValue
    campaigns.scheduleDevices(
      partiallyScheduledCampaign.id,
      partiallyScheduledCampaignDevices.take(nScheduled).toSeq).futureValue

    parent.childActorOf(CampaignSupervisor.props(
      director,
      schedulerPollingTimeout,
      schedulerDelay,
      schedulerBatchSize
    ))

    parent.expectMsg(3.seconds, CampaignsScheduled(Set(partiallyScheduledCampaign.id)))
    parent.expectMsg(3.seconds, CampaignComplete(partiallyScheduledCampaign.id))

    val freshCampaignDevices = Gen.listOfN(n, genDeviceId).generate.toSet
    campaigns.create(freshCampaign, Set.empty, freshCampaignDevices, Seq.empty).futureValue
    parent.expectMsg(3.seconds, CampaignsScheduled(Set(freshCampaign.id)))
  }
}

class CampaignSupervisorSpec2 extends ActorSpec[CampaignSupervisor] with CampaignerSpec with UpdateSupport {

  import CampaignSupervisor._
  import org.scalacheck.Arbitrary._

  val campaigns = Campaigns()

  def buildCampaignWithUpdate: Campaign = {
    val update = genMultiTargetUpdate.generate
    val updateId = updateRepo.persist(update).futureValue
    arbitrary[Campaign].generate.copy(updateId = updateId)
  }

  "campaign supervisor" should "clean out campaigns that are marked to be cancelled" in {
    val campaign = buildCampaignWithUpdate
    val parent   = TestProbe()
    val n        = Gen.choose(batch, batch * 2).generate
    val devs     = Gen.listOfN(n, genDeviceId).generate.toSet

    campaigns.create(campaign, Set.empty, devs, Seq.empty).futureValue

    parent.childActorOf(CampaignSupervisor.props(
      director,
      schedulerPollingTimeout,
      10.seconds,
      schedulerBatchSize
    ))
    parent.expectMsg(5.seconds, CampaignsScheduled(Set(campaign.id)))
    expectNoMessage(5.seconds)

    campaigns.cancel(campaign.id).futureValue
    parent.expectMsg(5.seconds, CampaignsCancelled(Set(campaign.id)))
    expectNoMessage(5.seconds)
  }

}
