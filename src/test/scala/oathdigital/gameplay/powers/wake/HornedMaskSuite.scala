package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Refusals,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class HornedMaskSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val mask = CatalogNames.relic("Horned Mask")
  private val source = DecisionOptionRef.Relic(mask)
  private val power = HornedMask(catalog)
  private val inn = CatalogNames.denizen("Wayside Inn")
  private val elders = CatalogNames.denizen("Elders")
  private val wolves = CatalogNames.denizen("Wolves")
  // A denizen with no restriction on discard.
  private val fresh = CatalogNames.denizen("Wrestlers")
  private val tongue = CatalogNames.denizen("Silver Tongue")
  private val piper = CatalogNames.denizen("Pied Piper")
  // Each is printed Locked: faceup, it cannot be discarded.
  private val locked = Vector("Faithful Friend", "A Small Favor",
    "Family Heirloom").map(CatalogNames.denizen(_))
  private val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(mask),
    HornedMask.id)
  // The actor's site, in the Cradle.
  private val home = CatalogNames.site("Ancient City")

  /** The actor in Wake at Ancient City, holding the Horned Mask faceup, with
    * no adviser. */
  private def staged = Table.start.turn(actor, Phase.Wake)
    .pawn(actor, home).relic(actor, mask).ready
  /** `staged`, with Wayside Inn at the actor's site. */
  private def innAtHome = Table.from(staged).denizen(inn, at = home).unchecked
  private def denizensHere(ready: ReadyGame): Vector[DenizenId] =
    ready.game.current.map.sites(home).denizens.collect {
      case d: DenizenState => d.id }
  private def adviser(ready: ReadyGame, id: DenizenId) = Look(ready)
    .player(actor).advisers
    .collectFirst { case d: DenizenState if d.id == id => d }
  private def choose(id: DenizenId) = pick(DecisionOptionRef.Denizen(id))
  /** Answers the Mask's denizen question with Wayside Inn. */
  private def takeInn(t: OathTransition) =
    accepted(answer(t, actor, HornedMask.denizenDecisionId, choose(inn)))
  /** The actor holds exactly `cards`, faceup, beside Wayside Inn. */
  private def holding(cards: DenizenId*) =
    cards.foldLeft(Table.from(innAtHome))(_.adviser(actor, _)).unchecked

  test("it offers the denizens at the site and never the edifice"):
    val ready = Table.from(innAtHome).denizen(elders, at = home)
      .edifice("Marble Fountains", EdificeSide.Intact, at = home).unchecked
    val t = accepted(use(ready, power, source))
    assert(awaits(t, HornedMask.denizenDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor).map(_.toSet),
      Some(denizensHere(ready).map(id => "denizen" -> id.value).toSet))
    assertEquals(denizensHere(ready).toSet, Set(inn, elders))

  test("a site-only denizen becomes a facedown adviser, and the use is " +
      "recorded once per turn"):
    val ready = innAtHome
    val t = accepted(use(ready, power, source))
    val done = takeInn(t)
    assert(!denizensHere(after(done)).contains(inn))
    assertEquals(adviser(after(done), inn).map(_.orientation),
      Some(Orientation.FaceDown))
    assertEquals(Look(after(done)).player(actor).advisers.map(_.id),
      Vector(inn))
    assert(after(done).game.current.turn.usedPowers.contains(used))
    // It was Wake's only option, so Wake ended with it: put the turn back in
    // Wake, its uses kept, to try again.
    parked.assertResumed(done.state, Phase.Act, actor)
    val again = Table.from(after(done)).backInPhase(Phase.Wake).unchecked
    assertEquals(use(again, power, source).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))

  test("resources on the taken card return by the standard returns"):
    val ready = Table.from(innAtHome).tokens(inn, favor = 2, secrets = 1)
      .unchecked
    val suit = catalog.suitOf(inn).get
    val t = accepted(use(ready, power, source))
    val done = takeInn(t)
    assertEquals(adviser(after(done), inn).map(_.tokens), Some(Tokens.empty))
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 2)
    assertEquals(Look(after(done)).faceDownSecrets(actor),
      Look(ready).faceDownSecrets(actor) + 1)

  test("a full adviser area asks which adviser to discard, and it goes to " +
      "the next region's discard pile"):
    val ready = holding(elders, fresh, wolves)
    val t = accepted(use(ready, power, source))
    val asked = takeInn(t)
    assert(awaits(asked, HornedMask.discardDecisionId),
      parked.parkedDecision(asked.state).toString)
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value,
      "denizen" -> wolves.value)))
    val done = accepted(answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)))
    // The Provinces follow the Cradle, the region of the actor's site.
    assertEquals(after(done).game.current.commonCards
      .discard(Region.Provinces).last, elders)
    assertEquals(Look(after(done)).player(actor).advisers.map(_.id).toSet,
      Set[CardId](fresh, wolves, inn))
    assertEquals(adviser(after(done), inn).map(_.orientation),
      Some(Orientation.FaceDown))
    assertEquals(replayed(ready, t.events ++ asked.events ++ done.events),
      Right(done.state))
    assertWireRoundTrips(t.events ++ asked.events ++ done.events)

  test("a locked adviser cannot be offered for discard"):
    val ready = holding(elders, fresh, locked.head)
    val t = accepted(use(ready, power, source))
    val asked = takeInn(t)
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value)))
    assertEquals(answer(asked, actor, HornedMask.discardDecisionId,
      choose(locked.head)).left.toOption,
      Some(Refusals.notOffered(HornedMask.discardDecisionId)))

  test("a facedown locked adviser can be offered for discard"):
    val ready = Table.from(holding(elders, fresh)).adviser(actor, locked.head,
      facedown = true).unchecked
    val t = accepted(use(ready, power, source))
    val asked = takeInn(t)
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value,
      "denizen" -> locked.head.value)))

  test("a Silver Tongue holder is full at two advisers, and Silver Tongue " +
      "itself is locked so only the other adviser can go"):
    val ready = holding(tongue, elders)
    val t = accepted(use(ready, power, source))
    val asked = takeInn(t)
    assert(awaits(asked, HornedMask.discardDecisionId),
      parked.parkedDecision(asked.state).toString)
    assertEquals(offered(asked, actor), Some(Vector("denizen" -> elders.value)))
    val done = accepted(answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)))
    assertEquals(Look(after(done)).player(actor).advisers.map(_.id).toSet,
      Set[CardId](tongue, inn))
    assertEquals(replayed(ready, t.events ++ asked.events ++ done.events),
      Right(done.state))

  test("a Silver Tongue holder holding two advisers, one of them Silver " +
      "Tongue and nothing else discardable, cannot use it"):
    val ready = holding(tongue, locked.head)
    assertEquals(use(ready, power, source).left.toOption,
      Some(OathViolation.CoreOperationRejected("locked",
        s"${tongue.value} is locked")))
    assert(denizensHere(ready).contains(inn))

  test("a facedown Silver Tongue does not lower the limit"):
    val ready = Table.from(innAtHome).adviser(actor, tongue, facedown = true)
      .adviser(actor, elders).unchecked
    val t = accepted(use(ready, power, source))
    val done = takeInn(t)
    parked.assertNotParked(done.state)
    assertEquals(Look(after(done)).advisers(actor).toSet,
      Set[CardId](tongue, elders, inn))

  test("a player who is not the Silver Tongue holder keeps the limit of three"):
    val ready = holding(elders, fresh)
    val t = accepted(use(ready, power, source))
    val done = takeInn(t)
    parked.assertNotParked(done.state)
    assertEquals(Look(after(done)).advisers(actor).toSet,
      Set[CardId](elders, fresh, inn))

  test("a Pied Piper holder is full at three other advisers, and Pied " +
      "Piper is shown unavailable for the discard"):
    val ready = holding(piper, elders, fresh, wolves)
    val asked = takeInn(accepted(use(ready, power, source)))
    assert(awaits(asked, HornedMask.discardDecisionId),
      parked.parkedDecision(asked.state).toString)
    assertEquals(queryOf(asked, actor).map(_.offeredOptions
      .map(option => (option.id, option.state, option.reason))), Some(Vector(
      (piper.value, "unavailable",
        Some("Does not count toward the adviser limit")),
      (elders.value, "available", None), (fresh.value, "available", None),
      (wolves.value, "available", None))))

  test("Pied Piper beside two other advisers leaves room"):
    val ready = holding(piper, elders, fresh)
    val done = takeInn(accepted(use(ready, power, source)))
    parked.assertNotParked(done.state)
    assertEquals(Look(after(done)).advisers(actor).toSet,
      Set[CardId](piper, elders, fresh, inn))

  test("a full area of locked advisers cannot use it, and it is not offered"):
    val ready = holding(locked*)
    assertEquals(use(ready, power, source).left.toOption,
      Some(OathViolation.CoreOperationRejected("locked",
        s"${locked.head.value} is locked")))
    assertEquals(PhasePowerHarness.menuPowers(catalog, ready, actor,
      PhasePowers(Vector(power)), WalkerPowers(Vector.empty,
        restrictionSet = OperationRestrictions.forCatalog(catalog))),
      Vector.empty)

  test("with no denizen at the site nothing is asked and nothing moves"):
    val ready = staged
    assertEquals(denizensHere(ready), Vector.empty)
    val t = accepted(use(ready, power, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).player(actor).advisers,
      Look(ready).player(actor).advisers)

  test("only the acting player answers, with an offered denizen"):
    val t = accepted(use(innAtHome, power, source))
    assertEquals(answer(t, Table.p2, HornedMask.denizenDecisionId,
      choose(inn)).left.toOption,
      Some(OathViolation.WrongPlayer(actor, Table.p2)))
    assertEquals(
      answer(t, actor, HornedMask.denizenDecisionId,
        choose(elders)).left.toOption,
      Some(Refusals.notOffered(HornedMask.denizenDecisionId)))

  test("it writes the denizen it took"):
    val t = accepted(use(innAtHome, power, source))
    val done = takeInn(t)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took ${inn.value} as a facedown adviser.",
      covers = false)))

  test("after a discard question the take is written apart from its action line"):
    val t = accepted(use(holding(elders, fresh, wolves), power, source))
    val asked = takeInn(t)
    val done = accepted(answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "taken", s"${actor.value} took ${inn.value} as a facedown adviser.",
      covers = false)))
