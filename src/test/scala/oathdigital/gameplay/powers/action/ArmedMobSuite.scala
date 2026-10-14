package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, ParkedNode,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class ArmedMobSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val mob = CatalogNames.denizen("Armed Mob")
  private val source = DecisionOptionRef.Denizen(mob)
  private val power = ArmedMob.forCatalog(catalog)
  // Two Hearth denizens whose powers these tests do not use.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val insomnia = CatalogNames.denizen("Insomnia")

  /** p1's Act at Ancient City beside a site Armed Mob, with `favor` favor.
    * Armed Mob is site-only. p2 holds the Darkest Secret. */
  /** The walk Armed Mob started and the walk after `card` was chosen. */
  private def staged(favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).pawn(p1, "Ancient City")
      .denizen(mob, at = "Ancient City").favor(p1, favor)
      .darkestSecret(Some(p2), 1)

  /** The discard pile of the region after Ancient City's (the Cradle):
    * where a card discarded there goes. */
  private def discarded(ready: ReadyGame): Vector[WorldCardId] =
    Look(ready).discardAfter("Ancient City")

  private def choose(ready: ReadyGame, card: DenizenId): UsedAndAnswered =
    useAndAnswer(ready, power, source,
      ArmedMob.decisionId, DecisionOptionRef.Denizen(card))

  private val yewStaff = "Yew Staff"

  /** The options of the decision the use is parked on, unavailable ones
    * included. */
  private def parkedChoiceOptions(t: OathTransition)
      : Vector[DecisionOption] =
    ParkedNode.of(t.state, catalog,
      phasePowers = PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.query match
          case DecisionQuery.ChooseOne(options, _, _) => options
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a parked decision, got $other")

  private def playerOption(player: PlayerId): DecisionOption =
    DecisionOption.Player(DecisionOptionRef.Player(player))

  test("it places a favor and offers the Darkest Secret holder's faceup " +
      "advisers, not facedown or locked ones"):
    val ready = staged().adviser(p2, homesteaders)
      .adviser(p2, landWarden, facedown = true).adviser(p2, insomnia).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, ArmedMob.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(mob), Tokens(1, 0))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> homesteaders.value)))

  test("the adviser is discarded to the next region's pile with its returns"):
    val ready = staged().adviser(p2, homesteaders)
      .tokens(homesteaders, favor = 1, secrets = 1).ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val UsedAndAnswered(t, done) = choose(ready, homesteaders)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p2), Vector.empty[CardId])
    assertEquals(discarded(end).lastOption, Some(homesteaders))
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("its line names the card and its owner, covering the Discarded line"):
    val done = choose(staged().adviser(p2, homesteaders).ready, homesteaders)
      .answered
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${homesteaders.value} from " +
        s"${p2.value}'s advisers.",
      covers = true)))

  test("the player may be the target"):
    val ready = staged().darkestSecret(Some(p1), 1).adviser(p1, homesteaders)
      .ready
    val done = choose(ready, homesteaders).answered
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])

  test("a holder of both banners is no target, and the line says so"):
    val ready = staged().peoplesFavor(Some(p2), 1).adviser(p2, homesteaders)
      .ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](homesteaders))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none",
      "No player held the Darkest Secret without the People's Favor.",
      covers = true)))

  test("a target with only a locked adviser keeps it, and the line says so"):
    val ready = staged().adviser(p2, insomnia).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](insomnia))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.empty", s"${p2.value} had no faceup adviser to discard.",
      covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).adviser(p2, homesteaders).ready
    assert(!usableNow(broke).exists(_.power.id == ArmedMob.id))
    assertEquals(use(broke, power, source).left.toOption,
      Some(OathViolation.InsufficientFavor(1, 0)))

  test("an enemy's Yew Staff makes them no target"):
    val ready = staged().darkestSecret(None, 1).relic(p2, yewStaff)
      .adviser(p2, homesteaders).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](homesteaders))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none",
      "No player held the Darkest Secret without the People's Favor.",
      covers = true)))

  test("with Yew Staff the player is a target beside the holder: it asks " +
      "whose adviser, then which"):
    val ready = staged().relic(p1, yewStaff).adviser(p1, homesteaders)
      .adviser(p2, landWarden).ready
    val started = accepted(use(ready, power, source))
    assert(awaits(started, ArmedMob.playerDecisionId))
    assertEquals(offered(started, p1),
      Some(Vector("player" -> p1.value, "player" -> p2.value)))
    val chose = accepted(answer(started, p1, ArmedMob.playerDecisionId,
      pick(DecisionOptionRef.Player(p1))))
    assert(awaits(chose, ArmedMob.decisionId))
    assertEquals(offered(chose, p1),
      Some(Vector("denizen" -> homesteaders.value)))
    val done = accepted(answer(chose, p1, ArmedMob.decisionId,
      pick(DecisionOptionRef.Denizen(homesteaders))))
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](landWarden))
    val events = started.events ++ chose.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${homesteaders.value} from " +
        s"${p1.value}'s advisers.",
      covers = true)))

  test("a target with no faceup adviser is shown unavailable"):
    val ready = staged().relic(p1, yewStaff).adviser(p1, homesteaders)
      .adviser(p2, landWarden, facedown = true).ready
    val started = accepted(use(ready, power, source))
    assertEquals(parkedChoiceOptions(started), Vector[DecisionOption](
      playerOption(p1), DecisionOption.Unavailable(playerOption(p2),
        OptionReason.Written(ArmedMob.noAdviser))))

  test("with Yew Staff and no other target it asks only for the player's " +
      "own adviser"):
    val ready = staged().darkestSecret(None, 1).relic(p1, yewStaff)
      .adviser(p1, homesteaders).ready
    val started = accepted(use(ready, power, source))
    assert(awaits(started, ArmedMob.decisionId))
    assertEquals(offered(started, p1),
      Some(Vector("denizen" -> homesteaders.value)))

  test("with the People's Favor, Yew Staff makes the player no target"):
    val ready = staged().peoplesFavor(Some(p1), 1).relic(p1, yewStaff)
      .adviser(p1, homesteaders).adviser(p2, landWarden).ready
    val started = accepted(use(ready, power, source))
    assert(awaits(started, ArmedMob.decisionId))
    assertEquals(offered(started, p1),
      Some(Vector("denizen" -> landWarden.value)))

  test("with two targets and no faceup adviser nothing is asked, and the " +
      "line says so"):
    val done = accepted(use(staged().relic(p1, yewStaff).ready, power,
      source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.empty-both", "Neither target had a faceup adviser to discard.",
      covers = true)))
