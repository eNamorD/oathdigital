package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Peace Envoy: one favor placed and one favor given per defense die, used
  * with no other plan, while the enemy's pawn shares the user's site. */
class PeaceEnvoySuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Peace Envoy")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val outriders = CatalogNames.denizen("Outriders")
  private val source = PowerSourceRef.Card(card)

  /** `b`, with the actor also holding Longbows faceup: a free plan that
    * keeps the attacker's window asked while Peace Envoy is withheld. */
  private def withLongbowsHeld(b: Board): Board =
    withAdviser(b, longbows, Orientation.FaceUp)

  /** The rival rules the origin with two warbands and the Oathkeeper
    * title, and stands there with no favor. The actor holds Peace Envoy
    * faceup and `favor` favor. Ancient City prints 2 defense dice. */
  private def envoyAttackerWithRivalAtOrigin(favor: Int): Board =
    val base = withEnemyAtOrigin(rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7)))
    on(withAdviser(base, card, Orientation.FaceUp))(
      _.favor(base.actor, favor).favor(base.rival, 0))

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * stands there, and holds Peace Envoy faceup and 5 favor. The actor has
    * no favor. */
  private def envoyDefenderAtOrigin: Board =
    val base = withEnemyAtOrigin(rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7)))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, 5).favor(base.actor, 0))

  test("the attacker places one favor and gives one per defense die, and " +
      "the defender is not asked"):
    val b = envoyAttackerWithRivalAtOrigin(favor = 5)
    val run = commit(rules(losingDice), b, 2)
    assertEquals(Look(run.state).diceInPool(CampaignIds.defensePool), 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    assertEquals(Look(picked.state).favor(b.actor), 2)
    assertEquals(Look(picked.state).favor(b.rival), 2)
    assert(!awaits(picked, b.rival, CampaignIds.defenderPlan))
    assertEquals(said(PeaceEnvoy, picked.events), Vector(NoteText.Said(
      "gave", s"Gave 2 favor to ${b.rival.value}.", covers = false)))

  test("its price counts the gift"):
    val b = envoyAttackerWithRivalAtOrigin(favor = 5)
    val price = commit(rules(losingDice), b, 2).options(b.actor).collectFirst {
      case priced: DecisionOption.Priced if priced.ref == ref => priced.price }
    assertEquals(price.map(_.favor), Some(3))

  test("a user who cannot give it all sees the shortfall"):
    val b = withLongbowsHeld(envoyAttackerWithRivalAtOrigin(favor = 2))
    val run = commit(rules(losingDice), b, 2)
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 3 favor; you have 2")))

  test("it is withheld unless the enemy's pawn is at the user's site"):
    // The rival rules the origin but stands elsewhere.
    val apart = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val away = withLongbowsHeld(on(withAdviser(apart, card,
      Orientation.FaceUp))(_.favor(apart.actor, 5)))
    assertEquals(commit(rules(losingDice), away, 2).withheld(away.actor, ref),
      Some(OptionReason.Written(PeaceEnvoy.Apart)))
    // Bandits have no pawn.
    val base = board(warbands = 5, supply = 7)
    val bandits = withLongbowsHeld(on(withAdviser(base, card,
      Orientation.FaceUp))(_.favor(base.actor, 5)))
    assertEquals(
      commit(rules(losingDice), bandits, 2).withheld(bandits.actor, ref),
      Some(OptionReason.Written(PeaceEnvoy.Apart)))

  test("it is used with no other plan"):
    val b = withAdviser(withLongbowsHeld(
      envoyAttackerWithRivalAtOrigin(favor = 5)), outriders,
      Orientation.FaceUp)
    val run = commit(rules(losingDice), b, 2)
    // Longbows and Outriders are withheld once Peace Envoy is chosen, so
    // nothing is asked.
    assert(!awaits(run.pick(b.actor, CampaignIds.attackerPlan, ref), b.actor,
      CampaignIds.attackerPlan))
    // Outriders keeps the window asked after Longbows.
    val other = run.pick(b.actor, CampaignIds.attackerPlan, longbowsRef)
    assert(awaits(other, b.actor, CampaignIds.attackerPlan))
    assertEquals(other.withheld(b.actor, ref), Some(OptionReason.Noted(
      PeaceEnvoy.id, PeaceEnvoy.notWithOthers(source))))

  test("a defender gives the attacker one favor per defense die"):
    val b = envoyDefenderAtOrigin
    val run = commit(rules(winningDice), b, 4).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(run.state).favor(b.actor), 2)
    // Five, less the favor placed (settled off turn) and the two given.
    assertEquals(Look(run.state).favor(b.rival), 2)
    assertEquals(said(PeaceEnvoy, run.events), Vector(NoteText.Said(
      "gave-off-turn",
      s"${b.rival.value} gave 2 favor to ${b.actor.value}.",
      covers = false)))

  test("its reasons read as ruled"):
    def sentence(note: PowerNote): String =
      NoteText.sentence(PeaceEnvoy.noteKeys, note)
    assertEquals(sentence(PeaceEnvoy.notWithOthers(source)),
      "Cannot be used with other battle plans.")
    assertEquals(sentence(PeaceEnvoy.noOthers(source)),
      "No other battle plans.")
    assertEquals(sentence(PeaceEnvoy.decided(source)),
      "The battle is decided.")
