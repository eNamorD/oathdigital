package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class HonorGuardSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val guard = CatalogNames.denizen("Honor Guard")
  private val source = DecisionOptionRef.Denizen(guard)
  private val power = HonorGuard.forCatalog(catalog)
  // Denizens whose powers these tests do not use.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val newsFromAfar = CatalogNames.denizen("News from Afar")
  private val levelers = CatalogNames.denizen("Levelers")
  private val codeOfHonor = CatalogNames.denizen("Code of Honor")
  private val insomnia = CatalogNames.denizen("Insomnia")
  private val home = CatalogNames.site("Ancient City")

  /** p1's Act at Ancient City holding Honor Guard with `favor` favor. p2
    * stands at Ancient City holding Homesteaders faceup with 1 favor and 1
    * secret on it. */
  /** The walk Honor Guard started and the walk after `card` was chosen. */
  private def staged(favor: Int = 3): Table =
    Table.start.turn(p1, Phase.Act).pawn(p1, home).adviser(p1, guard)
      .favor(p1, favor).pawn(p2, at = home).adviser(p2, homesteaders)
      .tokens(homesteaders, favor = 1, secrets = 1)

  private def choose(ready: ReadyGame, card: DenizenId): UsedAndAnswered =
    useAndAnswer(ready, power, source,
      HonorGuard.decisionId, DecisionOptionRef.Denizen(card))

  test("it places 2 favor, burns 1, and offers the faceup advisers of " +
      "players at the site with no faceup Order adviser"):
    val ready = staged().adviser(p1, landWarden)
      .adviser(p2, newsFromAfar, facedown = true).pawn(p3, at = home)
      .adviser(p3, codeOfHonor).adviser(p3, levelers).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, HonorGuard.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(guard), Tokens(2, 0))
    assertEquals(Look(after(t)).favor(p1), 0)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> homesteaders.value)))

  test("the adviser is buried with its returns"):
    val ready = staged().ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val UsedAndAnswered(t, done) = choose(ready, homesteaders)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p2), Vector.empty[CardId])
    assertEquals(end.game.current.commonCards.worldDeck.last, homesteaders)
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("its line names the card and its owner, covering the Buried line"):
    val done = choose(staged().ready, homesteaders).answered
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} buried ${homesteaders.value} from ${p2.value}'s advisers.",
      covers = true)))

  test("a locked adviser may be buried"):
    val done = choose(staged().adviser(p2, insomnia).ready, insomnia).answered
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](homesteaders))

  test("with nobody else at the site, the cost stays paid and the line says " +
      "so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, guard)
      .favor(p1, 3).adviser(p2, homesteaders).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](homesteaders))
    assertEquals(Look(after(done)).favor(p1), 0)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No adviser could be buried.", covers = true)))

  test("it is unusable with 2 favor"):
    val broke = staged(favor = 2).ready
    assert(!usableNow(broke).exists(_.power.id == HonorGuard.id))
    assert(use(broke, power, source).isLeft)
