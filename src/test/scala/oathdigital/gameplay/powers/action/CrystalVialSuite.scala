package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class CrystalVialSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val vial = RelicId("R21")
  private val source = DecisionOptionRef.Relic(vial)
  private val power = CrystalVial(catalog)
  private val inn = DenizenId("47")
  private val faith = VisionId("vision:vision-of-faith")
  private val held: DenizenId = player(base).advisers.collectFirst {
    case d: DenizenState => d.id }.get
  private val other = others(base).head

  private def staged = inPhase(withSecrets(withRelic(base, vial), actor, 2, 0),
    Phase.Act)
  private def relicOf(ready: ReadyGame) = player(ready).relics
    .find(_.id == vial).get
  private def secretsOf(ready: ReadyGame) =
    (player(ready).board.faceUpSecrets, player(ready).board.faceDownSecrets)
  private def siteCards(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.map {
      case d: DenizenState => "denizen" -> d.id.value
      case e: EdificeState => "edifice" -> e.id.value }
  private def worldDeck(ready: ReadyGame) = ready.game.current.commonCards.worldDeck
  private def withAdviserTokens(ready: ReadyGame, tokens: Tokens) =
    updateActor(ready)(p => p.copy(advisers = p.advisers.map {
      case d: DenizenState if d.id == held => d.copy(tokens = tokens)
      case unchanged => unchanged }))
  private def withSiteTokens(ready: ReadyGame, card: DenizenId, tokens: Tokens) =
    ready.updateCurrent { c =>
      val site = c.map.sites(home(ready))
      c.copy(map = c.map.copy(sites = c.map.sites.updated(home(ready),
        site.copy(denizens = site.denizens.map {
          case d: DenizenState if d.id == card => d.copy(tokens = tokens)
          case unchanged => unchanged }))))
    }
  /** The actor at a site with no cards, holding no advisers. */
  private def bare: ReadyGame = {
    val site = base.game.current.map.inPlay.last
    val cleared = base.updateCurrent { c =>
      val state = c.map.sites(site)
      c.copy(
        map = c.map.copy(sites = c.map.sites.updated(site,
          state.copy(denizens = Vector.empty))),
        commonCards = c.commonCards.copy(
          worldDeck = c.commonCards.worldDeck ++ state.denizens.collect {
            case d: DenizenState => d.id: WorldCardId },
          edificeDeck = c.commonCards.edificeDeck ++ state.denizens.collect {
            case e: EdificeState => e.id }))
    }
    inPhase(withSecrets(withRelic(withPawn(withoutAdvisers(cleared, actor),
      actor, site), vial), actor, 2, 0), Phase.Act)
  }

  test("Crystal Vial is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(CrystalVial.id).isDefined)
  }

  test("the cost is one secret placed on the Vial and one burnt") {
    val t = use(staged, power, source).toOption.get
    assert(awaits(t, CrystalVial.decisionId), t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(secretsOf(after(t)), (0, 0))
  }

  test("it offers the actor's advisers in either orientation and the cards " +
      "at the actor's site, and nobody else's") {
    val ready = atHome(giveVision(giveAdviser(staged, actor, DenizenId("26"),
      Orientation.FaceUp), actor, faith, Orientation.FaceDown), inn)
    val t = use(ready, power, source).toOption.get
    assertEquals(offered(t, actor).map(_.toSet), Some(Set(
      "denizen" -> held.value, "denizen" -> "26", "vision" -> faith.value) ++
      siteCards(ready)))
  }

  test("an adviser is buried at the bottom of the world deck, and its favor " +
      "and secrets return") {
    val ready = withAdviserTokens(staged, Tokens(2, 1))
    val suit = catalog.suitOf(held).get
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).toOption.get
    assert(!player(after(done)).advisers.exists(_.id == held))
    assertEquals(worldDeck(after(done)).last, held)
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 2)
    assertEquals(secretsOf(after(done)), (0, 1))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))
  }

  test("a Vision adviser can be buried") {
    val ready = giveVision(staged, actor, faith, Orientation.FaceDown)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Vision(faith))).toOption.get
    assert(!player(after(done)).advisers.exists(_.id == faith))
    assertEquals(worldDeck(after(done)).last, faith)
  }

  test("a denizen at the site is buried with its favor and secrets returned") {
    val ready = withSiteTokens(atHome(staged, inn), inn, Tokens(1, 1))
    val suit = catalog.suitOf(inn).get
    val t = use(ready, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(inn))).toOption.get
    assert(!siteCards(after(done)).contains("denizen" -> inn.value))
    assertEquals(worldDeck(after(done)).last, inn)
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 1)
    assertEquals(secretsOf(after(done)), (0, 1))
  }

  test("the edifice at the site is buried whichever face it shows") {
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val edifice = EdificeId("E15")
      val ready = withEdifice(staged, edifice, side, home(staged))
      val t = use(ready, power, source).toOption.get
      val done = answer(t, actor, CrystalVial.decisionId,
        pick(DecisionOptionRef.Edifice(edifice))).toOption.get
      assert(!siteCards(after(done)).contains("edifice" -> edifice.value),
        side.toString)
      assertEquals(after(done).game.current.commonCards.edificeDeck.last,
        edifice, side.toString)
    }
  }

  test("only the acting player answers, with an offered card") {
    val t = use(staged, power, source).toOption.get
    val theirs = player(base, other).advisers.collectFirst {
      case d: DenizenState => d.id }.get
    assert(answer(t, other, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).isLeft)
    assert(answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(theirs))).isLeft)
  }

  test("with no candidate the cost is paid and nothing else happens") {
    val ready = bare
    assertEquals(siteCards(ready), Vector.empty)
    val t = use(ready, power, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(secretsOf(after(t)), (0, 0))
  }

  test("it needs two faceup secrets, and is unusable while the Vial holds one") {
    Vector((1, 0), (1, 5)).foreach { case (up, down) =>
      val ready = withSecrets(staged, actor, up, down)
      assertEquals(usableNow(ready), Vector.empty, s"$up up, $down down")
      assert(use(ready, power, source).isLeft)
    }
    val t = use(staged, power, source).toOption.get
    val done = answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).toOption.get
    assertEquals(usableNow(withSecrets(after(done), actor, 2, 0)), Vector.empty)
  }
}
