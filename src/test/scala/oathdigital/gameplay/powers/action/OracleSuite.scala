package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{CardStaging, NoteText, PhasePowerCatalog,
  PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class OracleSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness.{after, awaits, parked, pick, usableNow}

  private val oracle = CatalogNames.denizen("Oracle")
  private val source = DecisionOptionRef.Denizen(oracle)
  private val faith = VisionRules.Faith
  private val vow = CatalogNames.denizen("Vow of Obedience")
  private val power = Oracle.forCatalog(catalog)
  private val placement = CardPlayProcedure.placementDecisionId(faith)
  /** The production walker rules and phase powers, so a Restriction such as
    * Vow of Obedience's hides what it forbids. */
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  /** p1's Act at Ancient City beside a site Oracle, with two faceup
    * secrets. Every other Vision lies in the Provinces discard pile. Faith is
    * the world deck's third card, under Threatening Roar and Fae Merchant,
    * or, `withVision = false`, in that pile too. */
  private def staged(withVision: Boolean = true): ReadyGame =
    val table = Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(oracle, at = "Ancient City").secrets(actor, 2)
      .discarded(Region.Provinces, VisionRules.Sanctuary,
        VisionRules.Rebellion, VisionRules.Conspiracy, VisionRules.Conquest)
    (if withVision then
      table.worldDeckTop("Threatening Roar", "Fae Merchant", faith)
    else table.discarded(Region.Provinces, faith)).ready
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
    val t = accepted(start(ready))
    assert(awaits(t, placement), parked.parkedDecision(t.state).toString)
    val drew = after(t)
    assertEquals(Look(drew).tokensOn(oracle), Tokens(0, 2))
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
    val t = accepted(start(ready))
    val done = accepted(place(t, "adviser-facedown"))
    parked.assertResumed(done.state, Phase.Act, actor)
    val held = after(done)
    assert(Look(held).player(actor).advisers.contains(
      VisionState(faith, Orientation.FaceDown)), Look(held).player(actor)
        .advisers.toString)
    assertEquals(held.game.current.temporaryHands.getOrElse(actor,
      Vector.empty), Vector.empty)
    val events = t.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("discarded, the Vision goes to a discard pile"):
    val t = accepted(start(staged()))
    val done = accepted(place(t, "discard"))
    parked.assertResumed(done.state, Phase.Act, actor)
    val located = CardIndex.from(after(done).game).toOption
      .flatMap(_.get(faith)).map(_.location.container)
    assert(located.exists {
      case CardContainer.RegionalDiscard(_) => true
      case _ => false
    }, located.toString)

  test("a faceup Vow of Obedience holder may not play it faceup"):
    val holding = Table.from(CardStaging.without(staged(), vow)).adviser(actor,
      vow).unchecked
    val t = accepted(start(holding))
    assert(!buttons(t).contains("adviser-faceup"), buttons(t).toString)
    assert(buttons(t).contains("adviser-facedown"))
    assert(buttons(t).contains("discard"))
    assert(place(t, "adviser-faceup").isLeft)

  test("its line names the Vision it drew"):
    val t = accepted(start(staged()))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${actor.value} drew ${faith.value} from the world deck.",
      covers = false)))

  test("a world deck with no Vision takes the cost and says so"):
    val ready = staged(withVision = false)
    val t = accepted(start(ready))
    parked.assertResumed(t.state, Phase.Act, actor)
    val paid = after(t)
    assertEquals(Look(paid).tokensOn(oracle), Tokens(0, 2))
    assertEquals(deckOf(paid), deckOf(ready))
    assertEquals(drawn(paid), drawn(ready))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", "The world deck held no Vision.", covers = false)))

  test("it is unusable without two faceup secrets"):
    val broke = Table.from(staged()).secrets(actor, 1, 1).unchecked
    assert(!usableNow(broke).exists(_.power.id == Oracle.id))
    assert(start(broke).isLeft)
