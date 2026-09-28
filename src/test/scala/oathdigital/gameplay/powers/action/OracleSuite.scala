package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{CardStaging, NoteText, PhasePowerCatalog,
  PowerFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.ParkedNode

class OracleSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture.{after, awaits, giveAdviser, parked, pick, usableNow,
    withSecrets}

  private val oracle = DenizenId("160")
  private val source = DecisionOptionRef.Denizen(oracle)
  private val faith = VisionRules.Faith
  private val vow = DenizenId("121")
  private val power = Oracle.forCatalog(catalog).get
  private val placement = CardPlayProcedure.placementDecisionId(faith)
  /** The production walker rules and phase powers, so a Restriction such as
    * Vow of Obedience's hides what it forbids. */
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  /** The actor stands beside a site Oracle with two faceup secrets. Every
    * Vision in the world deck waits in the Provinces discard pile, and Faith
    * is the deck's third card, or, `withVision = false`, nowhere in it. */
  private def staged(withVision: Boolean = true): ReadyGame =
    val ready = inPhase(withSecrets(atHome(CardStaging.without(base, faith),
      oracle), actor, 2, 0), Phase.Act)
    ready.updateCurrent { c =>
      val deck = c.commonCards.worldDeck
      val visions = deck.collect { case vision: VisionId => vision }
      val plain = deck.filterNot(visions.contains)
      c.copy(commonCards = c.commonCards.copy(
        worldDeck = if withVision then plain.take(2) ++ Vector(faith) ++
          plain.drop(2) else plain,
        regionalDiscards = c.commonCards.regionalDiscards.updated(
          Region.Provinces, c.commonCards.discard(Region.Provinces) ++
            visions)))
    }
  private def deckOf(ready: ReadyGame) = ready.game.current.commonCards.worldDeck
  private def drawn(ready: ReadyGame) = ready.game.current.tracks.visionsDrawn

  private def start(ready: ReadyGame): Either[OathViolation, OathTransition] =
    rules.startWalker(OathState.Ready(ready), ActionRef.UsePower(Oracle.id),
      actor, Vector.empty, Vector(source))
  private def place(from: OathTransition, button: String) =
    rules.resolveWalker(from.state, actor, placement,
      pick(DecisionOptionRef.Button(button)))
  private def replayed(from: ReadyGame, events: Vector[OathEvent]) =
    events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.Ready(from)))((state, event) =>
      state.flatMap(rules.evolve(_, event)))

  /** The buttons of the placement the walk is parked on, after the
    * restriction look-ahead. */
  private def buttons(from: OathTransition): Vector[String] =
    ParkedNode.of(from.state, catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case DecisionQuery.ChooseOne(options, _) => options.map(_.ref).collect {
          case DecisionOptionRef.Button(key) => key }
        case other => fail(s"not a choose-one: $other")
      case other => fail(s"not parked on a decision: $other")

  test("it places two secrets and draws the first Vision, the cards above " +
      "it staying in place"):
    val ready = staged()
    val t = start(ready).toOption.get
    assert(awaits(t, placement), parked.parkedDecision(t.state).toString)
    val drew = after(t)
    assertEquals(PaidActionHarness.tokensOn(drew, oracle), Tokens(0, 2))
    assertEquals(drew.game.current.temporaryHands.get(actor),
      Some(Vector(faith)))
    assertEquals(deckOf(drew), deckOf(ready).filterNot(_ == faith))
    assertEquals(drawn(drew), drawn(ready) + 1)
    assert(!drew.knowledge.advisers.getOrElse(actor, Vector.empty)
      .exists(deckOf(ready).take(2).contains))
    assertEquals(buttons(t).toSet,
      Set("discard", "adviser-faceup", "adviser-facedown"))

  test("played facedown, the Vision becomes an adviser; replay and the wire " +
      "agree"):
    val ready = staged()
    val t = start(ready).toOption.get
    val done = place(t, "adviser-facedown").toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    val held = after(done)
    assert(player(held).advisers.contains(
      VisionState(faith, Orientation.FaceDown)), player(held).advisers.toString)
    assertEquals(held.game.current.temporaryHands.getOrElse(actor,
      Vector.empty), Vector.empty)
    val events = t.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("discarded, the Vision goes to a discard pile"):
    val t = start(staged()).toOption.get
    val done = place(t, "discard").toOption.get
    parked.assertResumed(done.state, Phase.Act, actor)
    val located = CardIndex.from(after(done).game).toOption
      .flatMap(_.get(faith)).map(_.location.container)
    assert(located.exists {
      case CardContainer.RegionalDiscard(_) => true
      case _ => false
    }, located.toString)

  test("a faceup Vow of Obedience holder may not play it faceup"):
    val holding = giveAdviser(CardStaging.without(staged(), vow), actor, vow,
      Orientation.FaceUp)
    val t = start(holding).toOption.get
    assert(!buttons(t).contains("adviser-faceup"), buttons(t).toString)
    assert(buttons(t).contains("adviser-facedown"))
    assert(buttons(t).contains("discard"))
    assert(place(t, "adviser-faceup").isLeft)

  test("its line names the Vision it drew"):
    val t = start(staged()).toOption.get
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${actor.value} drew ${faith.value} from the world deck.",
      covers = false)))

  test("a world deck with no Vision takes the cost and says so"):
    val ready = staged(withVision = false)
    val t = start(ready).toOption.get
    parked.assertResumed(t.state, Phase.Act, actor)
    val paid = after(t)
    assertEquals(PaidActionHarness.tokensOn(paid, oracle), Tokens(0, 2))
    assertEquals(deckOf(paid), deckOf(ready))
    assertEquals(drawn(paid), drawn(ready))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", "The world deck held no Vision.", covers = false)))

  test("it is unusable without two faceup secrets"):
    val broke = withSecrets(staged(), actor, 1, 1)
    assert(!usableNow(broke).exists(_.power.id == Oracle.id))
    assert(start(broke).isLeft)
