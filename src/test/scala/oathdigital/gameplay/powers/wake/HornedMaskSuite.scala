package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class HornedMaskSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val mask = RelicId("R06")
  private val source = DecisionOptionRef.Relic(mask)
  private val power = HornedMask(catalog)
  private val inn = DenizenId("47")
  private val elders = DenizenId("26")
  private val wolves = DenizenId("39")
  private val fresh = DenizenId("1")
  private val tongue = DenizenId("92")
  private val locked = Vector(DenizenId("28"), DenizenId("15"), DenizenId("133"))
  private val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(mask),
    HornedMask.id)

  private def staged = inPhase(withRelic(base, mask), Phase.Wake)
  private def denizensHere(ready: ReadyGame): Vector[DenizenId] =
    ready.game.current.map.sites(home(ready)).denizens.collect {
      case d: DenizenState => d.id }
  private def adviser(ready: ReadyGame, id: DenizenId) = player(ready).advisers
    .collectFirst { case d: DenizenState if d.id == id => d }
  private def choose(id: DenizenId) = pick(DecisionOptionRef.Denizen(id))
  private def nextRegion(ready: ReadyGame): Region =
    val region = ready.game.current.map.regionOf(home(ready)).get
    Region.all((Region.all.indexOf(region) + 1) % Region.all.size)
  private def withSiteTokens(ready: ReadyGame, card: DenizenId, tokens: Tokens) =
    ready.updateCurrent { c =>
      val site = c.map.sites(home(ready))
      c.copy(map = c.map.copy(sites = c.map.sites.updated(home(ready),
        site.copy(denizens = site.denizens.map {
          case d: DenizenState if d.id == card => d.copy(tokens = tokens)
          case unchanged => unchanged }))))
    }
  /** The actor holds exactly `cards`, faceup, beside a site inn. */
  private def holding(cards: DenizenId*) = cards.foldLeft(
    withoutAdvisers(atHome(staged, inn), actor))((ready, id) =>
    giveAdviser(ready, actor, id, Orientation.FaceUp))

  test("it offers the denizens at the site and never the edifice"):
    val ready = withEdifice(atHome(atHome(staged, inn), elders),
      EdificeId("E15"), EdificeSide.Intact, home(staged))
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, HornedMask.denizenDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor).map(_.toSet),
      Some(denizensHere(ready).map(id => "denizen" -> id.value).toSet))
    assertEquals(denizensHere(ready).toSet, Set(inn, elders))

  test("a site-only denizen becomes a facedown adviser, and the use is " +
      "recorded once per turn"):
    val ready = atHome(staged, inn)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assert(!denizensHere(after(done)).contains(inn))
    assertEquals(adviser(after(done), inn).map(_.orientation),
      Some(Orientation.FaceDown))
    assertEquals(player(after(done)).advisers.map(_.id), Vector(inn))
    assert(after(done).game.current.turn.usedPowers.contains(used))
    // It was Wake's only option, so Wake ended with it: put the turn back in
    // Wake, its uses kept, to try again.
    parked.assertResumed(done.state, Phase.Act, actor)
    val again = after(done).updateCurrent(current =>
      current.copy(turn = current.turn.copy(phase = Phase.Wake)))
    assertEquals(use(again, power, source).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))

  test("resources on the taken card return by the standard returns"):
    val ready = withSiteTokens(atHome(staged, inn), inn, Tokens(2, 1))
    val suit = catalog.suitOf(inn).get
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assertEquals(adviser(after(done), inn).map(_.tokens), Some(Tokens.empty))
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 2)
    assertEquals(player(after(done)).board.faceDownSecrets,
      player(ready).board.faceDownSecrets + 1)

  test("a full adviser area asks which adviser to discard, and it goes to " +
      "the next region's discard pile"):
    val ready = holding(elders, fresh, wolves)
    val t = use(ready, power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assert(awaits(asked, HornedMask.discardDecisionId),
      parked.parkedDecision(asked.state).toString)
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value,
      "denizen" -> wolves.value)))
    val done = answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)).toOption.get
    assertEquals(after(done).game.current.commonCards
      .discard(nextRegion(ready)).last, elders)
    assertEquals(player(after(done)).advisers.map(_.id).toSet,
      Set[CardId](fresh, wolves, inn))
    assertEquals(adviser(after(done), inn).map(_.orientation),
      Some(Orientation.FaceDown))
    assertEquals(replayed(ready, t.events ++ asked.events ++ done.events),
      Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(
      t.events ++ asked.events ++ done.events))

  test("a locked adviser cannot be offered for discard"):
    val ready = holding(elders, fresh, locked.head)
    val t = use(ready, power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assertEquals(offered(asked, actor).map(_.toSet), Some(Set(
      "denizen" -> elders.value, "denizen" -> fresh.value)))
    assert(answer(asked, actor, HornedMask.discardDecisionId,
      choose(locked.head)).isLeft)

  test("a Silver Tongue holder is full at two advisers, and Silver Tongue " +
      "itself is locked so only the other adviser can go"):
    val ready = holding(tongue, elders)
    val t = use(ready, power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assert(awaits(asked, HornedMask.discardDecisionId),
      parked.parkedDecision(asked.state).toString)
    assertEquals(offered(asked, actor), Some(Vector("denizen" -> elders.value)))
    val done = answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)).toOption.get
    assertEquals(player(after(done)).advisers.map(_.id).toSet,
      Set[CardId](tongue, inn))
    assertEquals(replayed(ready, t.events ++ asked.events ++ done.events),
      Right(done.state))

  test("a Silver Tongue holder holding two advisers, one of them Silver " +
      "Tongue and nothing else discardable, takes nothing"):
    val ready = holding(tongue, locked.head)
    val t = use(ready, power, source).toOption.get
    parked.assertNotParked(t.state)
    assert(denizensHere(after(t)).contains(inn))

  test("a facedown Silver Tongue does not lower the limit"):
    val ready = giveAdviser(giveAdviser(withoutAdvisers(atHome(staged, inn),
      actor), actor, tongue, Orientation.FaceDown), actor, elders,
      Orientation.FaceUp)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    parked.assertNotParked(done.state)
    assertEquals(player(after(done)).advisers.size, 3)

  test("a player who is not the Silver Tongue holder keeps the limit of three"):
    val ready = holding(elders, fresh)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    parked.assertNotParked(done.state)
    assertEquals(player(after(done)).advisers.size, 3)

  test("a full area of locked advisers takes nothing and asks nothing"):
    val ready = holding(locked*)
    val t = use(ready, power, source).toOption.get
    parked.assertNotParked(t.state)
    assert(denizensHere(after(t)).contains(inn))
    assertEquals(player(after(t)).advisers, player(ready).advisers)

  test("with no denizen at the site nothing is asked and nothing moves"):
    val ready = staged
    assertEquals(denizensHere(ready), Vector.empty)
    val t = use(ready, power, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(player(after(t)).advisers, player(ready).advisers)

  test("only the acting player answers, with an offered denizen"):
    val t = use(atHome(staged, inn), power, source).toOption.get
    assert(answer(t, others(base).head, HornedMask.denizenDecisionId,
      choose(inn)).isLeft)
    assert(answer(t, actor, HornedMask.denizenDecisionId, choose(elders)).isLeft)

  test("it writes the denizen it took"):
    val t = use(atHome(staged, inn), power, source).toOption.get
    val done = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took ${inn.value} as a facedown adviser.",
      covers = false)))

  test("after a discard question the take is written apart from its action line"):
    val t = use(holding(elders, fresh, wolves), power, source).toOption.get
    val asked = answer(t, actor, HornedMask.denizenDecisionId, choose(inn))
      .toOption.get
    val done = answer(asked, actor, HornedMask.discardDecisionId,
      choose(elders)).toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "taken", s"${actor.value} took ${inn.value} as a facedown adviser.",
      covers = false)))
