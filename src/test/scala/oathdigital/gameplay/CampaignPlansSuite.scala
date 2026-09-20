package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans, CampaignSetup}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class CampaignPlansSuite extends munit.FunSuite {
  private def setupOf(b: Board, force: Int = 2): CampaignSetup = CampaignSetup(
    b.actor, CampaignKind.Conquest, b.origin,
    CampaignSetup.conquestDefender(b.ready, b.actor).get, Vector(b.origin),
    Vector.empty, force)

  private val outriders = cardWith("denizen.outriders")
  private val brass = relicWith("relic.brass-army.campaign")
  private def attacker(b: Board) = CampaignPlans.options(catalog, b.ready,
    setupOf(b), CampaignPlanSide.Attacker, b.actor)

  test("Outriders from a faceup adviser ignores skulls, from a facedown one it reveals first") {
    val up = attacker(withAdviser(board(), outriders, Orientation.FaceUp)).head
    assertEquals(up.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.IgnoreAttackSkulls))
    assertEquals(up.ref, DecisionOptionRef.Denizen(DenizenId(outriders)))
    val down = attacker(withAdviser(board(), outriders, Orientation.FaceDown)).head
    assertEquals(down.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.RevealSource, CampaignPlanEffect.IgnoreAttackSkulls))
  }

  test("Brass Army needs a faceup relic without tokens and a faceup secret") {
    val ready = withSecrets(withRelic(board(), brass), 1)
    val option = attacker(ready).head
    assertEquals(option.costs, Vector[CampaignPlanCost](CampaignPlanCost.Secret(1)))
    assertEquals(option.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddAttackDice(4)))
    assertEquals(attacker(withSecrets(withRelic(board(), brass), 0)), Vector.empty)
    assertEquals(attacker(board()), Vector.empty)
  }

  test("options are ordered adviser, relic, site card, then by stable key") {
    val both = withSecrets(withRelic(withAdviser(board(), outriders,
      Orientation.FaceUp), brass), 1)
    assertEquals(attacker(both).map(_.handlerId),
      Vector("denizen.outriders", "relic.brass-army.campaign"))
  }

  test("a defender plan with a cost is never offered") {
    val b = againstPlayer(board())
    val options = CampaignPlans.options(catalog, b.ready, setupOf(b).copy(
      defender = CampaignDefender.Player(b.other)), CampaignPlanSide.Defender,
      b.other)
    assert(options.forall(_.costs.isEmpty))
  }

  test("the title adds one defense die to an Oathkeeper and two to a Usurper") {
    val b = againstPlayer(board())
    def titled(side: TitleSide) = CampaignPlans.options(catalog,
      b.ready.updateCurrent(c => c.copy(title = c.title.copy(side = side))),
      setupOf(b).copy(defender = CampaignDefender.Player(b.other)),
      CampaignPlanSide.Defender, b.other).head
    assertEquals(titled(TitleSide.Oathkeeper).effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(1)))
    assertEquals(titled(TitleSide.Usurper).effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(2)))
    assertEquals(titled(TitleSide.Oathkeeper).ref, DecisionOptionRef.Button("title"))
  }

  test("only Outriders in the answers ignores skulls") {
    assertEquals(CampaignPlans.ignoresSkulls(catalog, Vector(
      DecisionOptionRef.Denizen(DenizenId(outriders)))), true)
    assertEquals(CampaignPlans.ignoresSkulls(catalog, Vector(
      DecisionOptionRef.Relic(RelicId(brass)))), false)
    assertEquals(CampaignPlans.ignoresSkulls(catalog, Vector.empty), false)
  }

  test("applying a plan pays its cost onto the card, reveals a facedown source and adds dice") {
    val b = withSecrets(withRelic(board(), brass), 1)
    val option = attacker(b).head
    assertEquals(CampaignPlans.apply(option, b.actor), Vector[CoreOperation](
      Move(Piece.Secrets(1), PositionedLocation(Location.PlayArea(b.actor)),
        PositionedLocation(Location.OnCard(RelicId(brass)))),
      ModifyDicePool(CampaignIds.attackPool, 4)))
    val down = withAdviser(board(), outriders, Orientation.FaceDown)
    assertEquals(CampaignPlans.apply(attacker(down).head, down.actor),
      Vector[CoreOperation](Move(Piece.Card(DenizenId(outriders)),
        PositionedLocation(Location.PlayArea(down.actor)),
        PositionedLocation(Location.PlayArea(down.actor)),
        resultingOrientation = Some(Orientation.FaceUp))))
  }
}
