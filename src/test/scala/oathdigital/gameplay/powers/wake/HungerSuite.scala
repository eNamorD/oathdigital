package oathdigital.gameplay.powers.wake

import oathdigital.application.{GameCommand, ParkedServiceFixture}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, Answers, Look, Outcomes,
  PhasePowerHarness, Refusals, Situation, Table}
import oathdigital.testkit.Table.{p2, p3}
import oathdigital.testkit.Unwrap.accepted

class HungerSuite extends munit.FunSuite:
  import PowerFixture.actor

  private val phasePowers = PhasePowerCatalog.default(catalog)
  private val rules = new OathRules(catalog, phasePowerCatalog = phasePowers)
  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = phasePowers)
  private val hunger = Hunger.forCatalog(catalog)
  private val card = hunger.cardId
  /** p2 wakes after p1's Rest; p3 is the third player. */
  private val (next, third) = (p2, p3)

  /** p1 in Act. p2 holds Hunger (faceup unless told otherwise) and Wrestlers;
    * p3 holds Rain Boots and shares p2's site, Broken Peaks. */
  private def staged(orientation: Orientation = Orientation.FaceUp): ReadyGame =
    Table.start
      .adviser(p2, card, facedown = orientation == Orientation.FaceDown)
      .adviser(p2, "Wrestlers")
      .adviser(p3, "Rain Boots")
      .pawn(p2, at = "Broken Peaks").pawn(p3, at = "Broken Peaks")
      .ready

  /** p3's Rain Boots with 1 favor and 2 secrets on it. Set on the
    * parked state, since the actor's Rest returns every card's tokens. */
  private def stocked(ready: ReadyGame): ReadyGame =
    Table.from(ready).tokens("Rain Boots", favor = 1, secrets = 2).unchecked

  private def rested(ready: ReadyGame) = accepted(
    rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest, actor))

  private def optionRefs(query: DecisionQuery): Vector[DecisionOptionRef] =
    query match
      case DecisionQuery.Partition(_, options, _, _) => options.map(_.ref)
      case _ => Vector.empty

  private def bury(from: OathTransition, slot: DecisionOptionRef) =
    rules.resolveWalker(from.state, next, Hunger.decisionId,
      DecisionAnswer.ChooseOneAnswer(slot))

  test("the waking holder must answer Hunger before anything else, choosing " +
      "among the advisers at their site but not Hunger"):
    val t = rested(staged())
    parked.assertParked(t.state, TriggeredProcedureRef.ForcedWake,
      Hunger.decisionId, next)
    val now = Outcomes.ready(t.state)
    assertEquals(now.game.current.turn, TurnState(next, Phase.Wake, Set.empty))
    val here = Look(now).player(next).pawnSite
    val expected = now.game.current.players.filter(_.pawnSite == here)
      .map(_.player).flatMap(owner =>
      Look(now).player(owner).advisers.zipWithIndex.collect {
        case (held, slot) if held.id != card =>
          DecisionOptionRef.AdviserSlot(owner, slot)
      })
    assertEquals(parked.parkedDecision(t.state).map(_.decision),
      Some(Hunger.decisionId))
    assertEquals(PhasePowerHarness.offered(t, next),
      Some(expected.map(slot => slot.kind -> slot.wireId)))
    assertEquals(
      rules.startWalker(t.state, PhaseTransitionRef.EndWake,
        next).left.toOption,
      Some(OathViolation.InvalidEventOrder("a walker procedure is already " +
        "pending")))
    assert(PhasePowerProcedure.usable(catalog, now, next, phasePowers, WalkerPowers.empty)
      .forall(_.power.id != Hunger.id))

  test("burying another player's adviser returns its favor and gives its " +
      "secrets to the holder"):
    val parkedAt = rested(staged())
    val before = stocked(Outcomes.ready(parkedAt.state))
    val t = parkedAt.copy(state = Ready(before))
    val victim = Look(before).player(third).advisers.head
    val suit = catalog.suitOf(victim.id).get
    val bank = before.banks.favor(suit)
    val secrets = Look(before).player(next).board
    val done = accepted(bury(t, DecisionOptionRef.AdviserSlot(third, 0)))
    val after = Outcomes.ready(done.state)
    assert(!Look(after).player(third).advisers.exists(_.id == victim.id))
    assertEquals(after.game.current.commonCards.worldDeck.last, victim.id)
    assertEquals(after.banks.favor(suit), bank + 1)
    val gained = Look(after).player(next).board
    assertEquals(gained.faceUpSecrets + gained.faceDownSecrets,
      secrets.faceUpSecrets + secrets.faceDownSecrets + 2)
    assert(done.events.contains(WalkerCompleted(TriggeredProcedureRef.ForcedWake)))
    assertEquals(NoteText.said(hunger, done.events), Vector(NoteText.Said(
      "buried", s"${next.value} buried ${victim.id.value} from " +
        s"${third.value}'s advisers.",
      covers = true)))
    assertEquals(ActPowerHarness.replayed(rules, before, done.events), after)

  test("a slot that is not offered is rejected"):
    val t = rested(staged())
    assertEquals(bury(t, DecisionOptionRef.AdviserSlot(actor, 0)).left.toOption,
      Some(Refusals.notOffered(Hunger.decisionId)))

  test("with nothing to bury, Hunger says so and Wake goes on"):
    // p2 wakes alone at Broken Peaks, holding only Hunger. The site's two
    // secrets give p2 a Wake option, so the Wake goes on once Hunger is done.
    val alone = Table.start
      .pawn(Table.p1, at = "Ancient City").pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").adviser(p2, card)
      .siteTokens("Broken Peaks", secrets = 2)
      .ready
    val t = rested(alone)
    parked.assertResumed(t.state, Phase.Wake, next)
    assertEquals(NoteText.said(hunger, t.events), Vector(NoteText.Said(
      "none", "No adviser could be buried.", covers = true)))

  test("a facedown Hunger does nothing"):
    // With no forced step and no other option, the next player's Wake ends
    // at once.
    val t = rested(staged(Orientation.FaceDown))
    parked.assertResumed(t.state, Phase.Act, next)
    assert(!t.events.contains(WalkerCompleted(TriggeredProcedureRef.ForcedWake)))
    assert(t.events.contains(WalkerCompleted(PhaseTransitionRef.EndWake)))

  test("a Hunger revealed in Setup's Reveal Cards runs at the first Wake"):
    // Replays Setup from its first command on purpose: Setup's Reveal Cards
    // is the subject.
    val ParkedServiceFixture.ChronicleWithDeckTop(chronicle, orders) =
      ParkedServiceFixture.withWorldDeckTop(FirstGameSetupFixture.chronicle,
        FirstGameSetupFixture.orders, Vector(card))
    val hungerRef = DecisionOptionRef.Denizen(card)
    val keepAndReveal: Answers =
      case park if park.decisionId ==
          SetupProcedure.adviserDecisionId(park.awaiting) &&
          optionRefs(park.decide.query).contains(hungerRef) =>
        DecisionAnswer.PartitionAnswer(optionRefs(park.decide.query).map(ref =>
          DecisionPlacement(ref, if ref == hungerRef then
            SetupProcedure.adviserKeepKey else SetupProcedure.adviserDiscardKey)))
      case park if park.decisionId ==
          SetupProcedure.revealDecisionId(park.awaiting) &&
          Look(park.ready).player(park.awaiting)
            .advisers.exists(_.id == card) =>
        DecisionAnswer.ChooseManyAnswer(Vector(DecisionOptionRef.AdviserSlot(
          park.awaiting, Look(park.ready).player(park.awaiting).advisers
            .indexWhere(_.id == card))))
    val begun = Situation.start(Situation.rules(catalog,
      phasePowers = phasePowers).withAnswers(keepAndReveal))
      .parkedAfter(GameCommand.Begin(chronicle, orders))
    val first = begun.ready.setup.firstPlayer
    // Hunger tops the world deck; it trades places with the first card of the
    // first player's setup hand.
    val dealt = begun.ready.updateCurrent(current =>
      val hand = current.temporaryHands(first)
      val deck = current.commonCards.worldDeck
      current.copy(
        temporaryHands = current.temporaryHands.updated(first,
          hand.updated(0, card)),
        commonCards = current.commonCards.copy(worldDeck =
          deck.updated(deck.indexOf(card), hand.head))))
    val woken = begun.copy(state = Ready(dealt)).after()
    assert(Look(woken.ready).player(first).advisers.exists {
      case DenizenState(id, orientation, _) =>
        id == card && orientation == Orientation.FaceUp
      case _ => false
    }, Look(woken.ready).player(first).advisers)
    assert(woken.events.contains(
      WalkerCompleted(TriggeredProcedureRef.ForcedWake)))
    assertEquals(woken.ready.game.current.turn.activePlayer, first)
    // The forced step was that Wake's only business, so Wake ended after it.
    assertEquals(woken.events.collect { case WalkerCompleted(procedure) =>
      procedure }.takeRight(2),
      Vector(TriggeredProcedureRef.ForcedWake, PhaseTransitionRef.EndWake))
    assertEquals(woken.ready.game.current.turn.phase, Phase.Act)
    assertEquals(NoteText.said(hunger, woken.events).map(_.key),
      Vector("buried"))
