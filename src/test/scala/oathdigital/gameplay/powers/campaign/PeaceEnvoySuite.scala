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

  /** Peace Envoy's gift lines in `events`, without its victory line. */
  private def giftLines(events: Vector[OathEvent]): Vector[NoteText.Said] =
    said(PeaceEnvoy, events).filter(line =>
      line.key == "gave" || line.key == "gave-off-turn")

  /** Whether `ops` kill any warband from `player`'s board. */
  private def killsFromBoard(ops: Vector[CoreOperation], player: PlayerId)
      : Boolean = ops.exists {
    case Kill(Piece.Warbands(_, _),
        PositionedLocation(Location.PlayArea(`player`), _, _)) => true
    case _ => false
  }

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
    assertEquals(giftLines(picked.events), Vector(NoteText.Said(
      "gave", s"Gave 2 favor to ${b.rival.value}.", covers = false)))

  test("an attacker campaigning from an origin finds the enemy there"):
    // The rival stands alone at the origin, away from the attacker's pawn;
    // a Raid's pawn prints 2 defense dice, so the gift is 2 favor.
    val (base, away) = rivalAloneAtASiteTheActorHolds(board = 2, atSite = 1)
    val b = on(withAdviser(base, card, Orientation.FaceUp))(
      _.favor(base.actor, 5).favor(base.rival, 0))
    val game = rules(losingDice)
    val run = committed(game, campaignQueuedFrom(game, b, away,
      PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))), b,
      force = 2, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    assertEquals(giftLines(picked.events), Vector(NoteText.Said(
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

  test("the shortfall counts a surcharge paid before the gift"):
    // The rival's Insect Swarm burns one more favor for each attacker plan.
    val base = withLongbowsHeld(envoyAttackerWithRivalAtOrigin(favor = 3))
    val b = withAdviserFor(base, base.rival,
      CatalogNames.denizen("Insect Swarm"), Orientation.FaceUp)
    val run = commit(rules(losingDice), b, 2)
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 4 favor; you have 3")))

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
    assertEquals(giftLines(run.events), Vector(NoteText.Said(
      "gave-off-turn",
      s"${b.rival.value} gave 2 favor to ${b.actor.value}.",
      covers = false)))

  test("a defender who cannot give it all sees the shortfall"):
    val base = envoyDefenderAtOrigin
    val b = on(base)(_.favor(base.rival, 2))
    // The title's defense keeps the defender's window asked.
    val run = commit(rules(winningDice), b, 4).finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 3 favor; you have 2")))

  test("its reasons read as ruled"):
    def sentence(note: PowerNote): String =
      NoteText.sentence(PeaceEnvoy.noteKeys, note)
    assertEquals(sentence(PeaceEnvoy.notWithOthers(source)),
      "Cannot be used with other battle plans.")
    assertEquals(sentence(PeaceEnvoy.noOthers(source)),
      "No other battle plans.")
    assertEquals(sentence(PeaceEnvoy.decided(source)),
      "The battle is decided.")

  test("an attacker wins a Conquest without a roll, and the defender's " +
      "warbands all return to their board"):
    val b = envoyAttackerWithRivalAtOrigin(favor = 5)
    val done = commit(rules(losingDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(done.result.attackFaces, Vector.empty)
    assertEquals(done.result.defenseFaces, Vector.empty)
    assertEquals(done.result.sacrificed, 0)
    assertEquals(Look(done.state).boardWarbands(b.rival),
      Look(b.ready).boardWarbands(b.rival) + 2)
    // Emptied and left unplaced, the origin refills with bandits after the
    // command.
    assert(Look(done.state).forces(b.origin) match
      case SiteForces.Occupied(ForceKind.Bandit, _) => true
      case _ => false)
    assert(!killsFromBoard(done.ops, b.actor))
    assertEquals(said(PeaceEnvoy, done.events), Vector(
      NoteText.Said("gave", s"Gave 2 favor to ${b.rival.value}.",
        covers = false),
      NoteText.Said("won", "Won the battle without a roll.",
        covers = false)))

  test("a defender wins without a roll, and the attacker loses no warband"):
    val b = envoyDefenderAtOrigin
    val done = commit(rules(winningDice), b, 4).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(done.result.attackFaces, Vector.empty)
    assert(!killsFromBoard(done.ops, b.actor))
    assertEquals(Look(done.state).boardWarbands(b.actor), 5)
    assertEquals(said(PeaceEnvoy, done.events).map(_.text), Vector(
      s"${b.rival.value} gave 2 favor to ${b.actor.value}.",
      s"${b.rival.value} won the battle without a roll."))

  test("a Raid won with it kills none of the defender's board"):
    val raid = raidBoard(defenderWarbands = 3)
    val b = on(withAdviser(raid.board, card, Orientation.FaceUp))(
      _.favor(raid.board.actor, 5))
    val done = commit(rules(losingDice), b, 2, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(!killsFromBoard(done.ops, b.rival))
    assertEquals(Look(done.state).boardWarbands(b.rival), 3)
