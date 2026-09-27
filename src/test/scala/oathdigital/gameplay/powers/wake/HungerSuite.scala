package oathdigital.gameplay.powers.wake

import oathdigital.application.{GameCommand, ParkedServiceFixture}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Situation

class HungerSuite extends munit.FunSuite:
  import PowerFixture.{actor, base, player}
  import TargetsFixture.{giveAdviser, updatePlayer, withPawn}

  private val phasePowers = PhasePowerCatalog.default(catalog)
  private val rules = new OathRules(catalog, phasePowerCatalog = phasePowers)
  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = phasePowers)
  private val hunger = Hunger.forCatalog(catalog).get
  private val card = hunger.cardId
  private val next = FinishRestProcedure.turnOrder(base)(1)
  private val third = base.game.current.players.map(_.player)
    .find(p => p != actor && p != next).get

  /** The actor in Act. `next` holds Hunger (faceup unless told otherwise)
    * and shares its site with `third`. */
  private def staged(orientation: Orientation = Orientation.FaceUp): ReadyGame =
    val act = base.updateCurrent(_.copy(turn = TurnState(actor, Phase.Act,
      Set.empty)))
    val site = player(act, next).pawnSite.get
    withPawn(giveAdviser(act, next, card, orientation), third, site)

  /** `third`'s starting adviser with 1 favor and 2 secrets on it. Set on the
    * parked state, since the actor's Rest returns every card's tokens. */
  private def stocked(ready: ReadyGame): ReadyGame =
    updatePlayer(ready, third)(p => p.copy(advisers = p.advisers.zipWithIndex.map {
      case (d: DenizenState, 0) => d.copy(tokens = Tokens(1, 2))
      case (other, _) => other }))

  private def rested(ready: ReadyGame) = rules.startWalker(Ready(ready),
    PhaseTransitionRef.BeginRest, actor).toOption.get

  private def ready(state: OathState) = state.asInstanceOf[Ready].value

  private def bury(from: OathTransition, slot: DecisionOptionRef) =
    rules.resolveWalker(from.state, next, Hunger.decisionId,
      DecisionAnswer.ChooseOneAnswer(slot))

  test("Hunger is a registered, forced WAKE power"):
    assert(phasePowers.find(Hunger.id).isDefined)
    assert(hunger.forced)
    assertEquals(hunger.timing, PowerTiming.Wake)

  test("the waking holder must answer Hunger before anything else, choosing " +
      "among the advisers at their site but not Hunger"):
    val t = rested(staged())
    parked.assertParked(t.state, TriggeredProcedureRef.ForcedWake,
      Hunger.decisionId, next)
    val now = ready(t.state)
    assertEquals(now.game.current.turn, TurnState(next, Phase.Wake, Set.empty))
    val here = player(now, next).pawnSite
    val expected = now.game.current.players.filter(_.pawnSite == here)
      .map(_.player).flatMap(owner =>
      player(now, owner).advisers.zipWithIndex.collect {
        case (held, slot) if held.id != card =>
          DecisionOptionRef.AdviserSlot(owner, slot)
      })
    assertEquals(parked.parkedDecision(t.state).map(_.decision),
      Some(Hunger.decisionId))
    assertEquals(TargetsFixture.offered(t, next).map(_.map(_._1).distinct),
      Some(Vector("adviser-slot")))
    assertEquals(TargetsFixture.offered(t, next).map(_.size), Some(expected.size))
    assert(rules.startWalker(t.state, PhaseTransitionRef.EndWake, next).isLeft)
    assert(PhasePowerProcedure.usable(catalog, now, next, phasePowers)
      .forall(_.power.id != Hunger.id))

  test("burying another player's adviser returns its favor and gives its " +
      "secrets to the holder"):
    val parkedAt = rested(staged())
    val before = stocked(ready(parkedAt.state))
    val t = parkedAt.copy(state = Ready(before))
    val victim = player(ready(t.state), third).advisers.head
    val suit = catalog.suitOf(victim.id).get
    val bank = ready(t.state).banks.favor(suit)
    val secrets = player(ready(t.state), next).board
    val done = bury(t, DecisionOptionRef.AdviserSlot(third, 0)).toOption.get
    val after = ready(done.state)
    assert(!player(after, third).advisers.exists(_.id == victim.id))
    assertEquals(after.game.current.commonCards.worldDeck.last, victim.id)
    assertEquals(after.banks.favor(suit), bank + 1)
    val gained = player(after, next).board
    assertEquals(gained.faceUpSecrets + gained.faceDownSecrets,
      secrets.faceUpSecrets + secrets.faceDownSecrets + 2)
    assert(done.events.contains(WalkerCompleted(TriggeredProcedureRef.ForcedWake)))
    assertEquals(NoteText.said(hunger, done.events), Vector(NoteText.Said(
      "buried", s"${next.value} buried ${victim.id.value} from " +
        s"${third.value}'s advisers.",
      covers = true)))
    assertEquals(done.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(before)))((s, e) => s.flatMap(rules.evolve(_, e))),
      Right(done.state))

  test("a slot that is not offered is rejected"):
    val t = rested(staged())
    assert(bury(t, DecisionOptionRef.AdviserSlot(actor, 0)).isLeft)

  test("with nothing to bury, Hunger says so and Wake goes on"):
    val ready = staged()
    val site = player(ready, next).pawnSite
    val elsewhere = ready.game.current.map.inPlay.find(s => !site.contains(s)).get
    val moved = Vector(actor, third).foldLeft(ready)((r, p) =>
      withPawn(r, p, elsewhere))
    val alone = updatePlayer(moved, next)(p =>
      p.copy(advisers = p.advisers.filter(_.id == card)))
    val t = rested(alone)
    parked.assertResumed(t.state, Phase.Wake, next)
    assertEquals(NoteText.said(hunger, t.events), Vector(NoteText.Said(
      "none", "No adviser could be buried.", covers = true)))

  test("a facedown Hunger does nothing"):
    val t = rested(staged(Orientation.FaceDown))
    parked.assertResumed(t.state, Phase.Wake, next)
    assert(!t.events.contains(WalkerCompleted(TriggeredProcedureRef.ForcedWake)))

  test("a Hunger faceup when Setup ends runs at the first Wake"):
    // Stands in for Setup's Reveal Cards step (ROADMAP cleanup), which does
    // not exist yet: Hunger is turned faceup while Setup is parked.
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders,
      Vector(card))
    val begun = Situation.start(Situation.rules(catalog,
      phasePowers = phasePowers)).parkedAfter(GameCommand.Begin(chronicle, orders))
    val first = begun.ready.setup.firstPlayer
    val revealed = begun.copy(state = Ready(giveAdviser(begun.ready, first,
      card, Orientation.FaceUp)))
    val woken = revealed.after()
    assert(woken.events.contains(
      WalkerCompleted(TriggeredProcedureRef.ForcedWake)))
    assertEquals(woken.ready.game.current.turn.activePlayer, first)
    assertEquals(woken.ready.game.current.turn.phase, Phase.Wake)
    assertEquals(NoteText.said(hunger, woken.events).map(_.key),
      Vector("buried"))

  test("Hunger cannot be used as an optional power"):
    val t = rested(staged(Orientation.FaceDown))
    val faceup = ready(t.state).updateCurrent(c => c.copy(players =
      c.players.map(p => if p.player != next then p else p.copy(advisers =
        p.advisers.map {
          case d: DenizenState if d.id == card =>
            d.copy(orientation = Orientation.FaceUp)
          case other => other }))))
    assert(rules.startWalker(Ready(faceup), ActionRef.UsePower(Hunger.id), next,
      Vector.empty, Vector(DecisionOptionRef.Denizen(card))).isLeft)
