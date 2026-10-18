package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.PowerNoted
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Vow of Peace's second sentence: attackers cannot sacrifice warbands to
  * increase their attack against a faceup holder.
  */
class VowOfPeaceSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Peace")
  private val wrestlers = CatalogNames.denizen("Wrestlers")

  private def defendedBy(b: Board, orientation: Orientation): Board =
    withAdviserFor(b, b.rival, vow, orientation)

  /** Every decision the Campaign parks on until it ends, answering the plan
    * windows with Finish and every amount with zero.
    */
  private def decisionsUntilTheCampaignEnds(run: Run): Vector[String] =
    parked.parkedDecision(run.state) match
      case Some(facts) if facts.decision == CampaignIds.attackerPlan ||
          facts.decision == CampaignIds.defenderPlan =>
        val next = run.answer(facts.awaiting, facts.decision,
          DecisionAnswer.ChooseOneAnswer(CampaignIds.finish))
        facts.decision +: decisionsUntilTheCampaignEnds(next)
      case Some(facts) if facts.decision == CampaignIds.sacrifice ||
          facts.decision == CampaignIds.placement =>
        val next = run.answer(facts.awaiting, facts.decision,
          DecisionAnswer.ChooseAmountAnswer(0))
        facts.decision +: decisionsUntilTheCampaignEnds(next)
      case Some(facts) => Vector(facts.decision)
      case None => Vector.empty

  test("an attacker against a faceup holder is not asked to sacrifice, and " +
      "the battle still resolves"):
    val defended = defendedBy(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)),
      Orientation.FaceUp)
    val run = commit(rules(losingDice), defended, 2)
    assert(!decisionsUntilTheCampaignEnds(run).contains(CampaignIds.sacrifice))
    // Two hollow-sword dice against the defender's two warbands: the defender
    // wins.
    assertEquals(run.finishPlanWindows.winner, Some(false))

  test("a Raid against a faceup holder is protected too"):
    val raided = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val run = commit(rules(losingDice),
      defendedBy(raided, Orientation.FaceUp), 2, raid = true)
    assert(!decisionsUntilTheCampaignEnds(run).contains(CampaignIds.sacrifice))

  test("an attacker against anyone else is still asked"):
    val against = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    assert(decisionsUntilTheCampaignEnds(commit(rules(losingDice), against, 2))
      .contains(CampaignIds.sacrifice))

  test("a facedown Vow of Peace protects no one"):
    val against = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val defended = defendedBy(against, Orientation.FaceDown)
    assert(decisionsUntilTheCampaignEnds(commit(rules(losingDice), defended, 2))
      .contains(CampaignIds.sacrifice))

  test("a holder who is not the defender is not protected"):
    val defended = defendedBy(board(warbands = 5, supply = 7),
      Orientation.FaceUp)
    assert(decisionsUntilTheCampaignEnds(commit(rules(losingDice), defended, 2))
      .contains(CampaignIds.sacrifice))

  test("the defender's own sacrifice is unaffected"):
    val defended = defendedBy(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)),
      Orientation.FaceUp)
    val b = withAdviserFor(defended, defended.rival, wrestlers,
      Orientation.FaceUp)
    val run = commit(rules(losingDice), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(wrestlers))
    // Wrestlers sacrifices the defender's one warband at the origin.
    assertEquals(picked.since(run).collect { case s: Sacrifice => s },
      Vector(Sacrifice(b.rival, Piece.Warbands(
        ForceKind.Exile(b.player(b.rival).lineage), 1),
        PositionedLocation(Location.Site(b.origin)))))

  /** Vow of Peace's own notes. The Campaign journals its own lines too
    * (procedures journal their own notes design, slice 4), and this suite
    * is about the one power. */
  private def notes(run: Run): Vector[PowerNoted] =
    run.finishPlanWindows.events.collect {
      case noted: PowerNoted if noted.power == VowOfPeaceContribution.id =>
        noted }

  test("the removed sacrifice decision leaves a note naming the defender"):
    val b = defendedBy(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)),
      Orientation.FaceUp)
    assertEquals(notes(commit(rules(losingDice), b, 2)), Vector(PowerNoted(
      VowOfPeaceContribution.id, VowOfPeaceContribution.noSacrifice(
        PowerSourceRef.Card(vow), NoteArg.Player(b.rival)),
      covers = false)))

  test("an attacker against anyone else writes no Vow of Peace note"):
    val against = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    assertEquals(notes(commit(rules(losingDice), against, 2)), Vector.empty)
