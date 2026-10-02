package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class HonorGuardSuite extends munit.FunSuite:
  import TargetsFixture._

  private val guard = CatalogNames.denizen("Honor Guard")
  private val source = DecisionOptionRef.Denizen(guard)
  private val power = HonorGuard.forCatalog(catalog)
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val insomnia = CatalogNames.denizen("Insomnia")
  private val home = Table.homeOf(p1)

  /** p1's Act holding Honor Guard with `favor` favor. p2 stands at p1's site
    * holding plain(0) faceup with 1 favor and 1 secret on it. */
  private def staged(favor: Int = 3): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, guard).favor(p1, favor)
      .pawn(p2, at = home).adviser(p2, plain(0))
      .tokens(plain(0), favor = 1, secrets = 1)

  private def choose(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, HonorGuard.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places 2 favor, burns 1, and offers the faceup advisers of " +
      "players at the site with no faceup Order adviser"):
    val ready = staged().adviser(p1, plain(1))
      .adviser(p2, plain(2), facedown = true).pawn(p3, at = home)
      .adviser(p3, order(0)).adviser(p3, plain(3)).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, HonorGuard.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(guard), Tokens(2, 0))
    assertEquals(Look(after(t)).favor(p1), 0)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> plain(0).value)))

  test("the adviser is buried with its returns"):
    val ready = staged().ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val (t, done) = choose(ready, plain(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p2), Vector.empty[CardId])
    assertEquals(end.game.current.commonCards.worldDeck.last, plain(0))
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the card and its owner, covering the Buried line"):
    val (_, done) = choose(staged().ready, plain(0))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} buried ${plain(0).value} from ${p2.value}'s advisers.",
      covers = true)))

  test("a locked adviser may be buried"):
    val (_, done) = choose(staged().adviser(p2, insomnia).ready, insomnia)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](plain(0)))

  test("with nobody else at the site, the cost stays paid and the line says " +
      "so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, guard)
      .favor(p1, 3).adviser(p2, plain(0)).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](plain(0)))
    assertEquals(Look(after(done)).favor(p1), 0)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No adviser could be buried.", covers = true)))

  test("it is unusable with 2 favor"):
    val broke = staged(favor = 2).ready
    assert(!usableNow(broke).exists(_.power.id == HonorGuard.id))
    assert(use(broke, power, source).isLeft)
