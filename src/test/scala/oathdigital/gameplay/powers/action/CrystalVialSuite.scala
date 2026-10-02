package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class CrystalVialSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val vial = CatalogNames.relic("Crystal Vial")
  private val source = DecisionOptionRef.Relic(vial)
  private val power = CrystalVial(catalog)
  private val inn = CatalogNames.denizen("Wayside Inn")
  private val faith = VisionRules.Faith
  private val elders = CatalogNames.denizen("Elders")
  /** p1's adviser, and p2's. */
  private val held = CatalogNames.denizen("Wrestlers")
  private val theirs = CatalogNames.denizen("Battle Honors")
  private val other = p2

  /** p1, in Act at Ancient City, holds the Vial and Wrestlers, with 2
    * faceup secrets; p2 holds Battle Honors. */
  private def staged = Table.start
    .pawn(p1, "Ancient City").relic(p1, vial).adviser(p1, held)
    .secrets(p1, faceUp = 2)
    .adviser(p2, theirs)
    .ready
  private def secretsOf(ready: ReadyGame) =
    (Look(ready).player(actor).board.faceUpSecrets, Look(ready).player(actor)
      .board.faceDownSecrets)
  private def siteCards(ready: ReadyGame) = ready.game.current.map
    .sites(actorsSite(ready)).denizens.map {
      case d: DenizenState => "denizen" -> d.id.value
      case e: EdificeState => "edifice" -> e.id.value }
  private def worldDeck(ready: ReadyGame) = ready.game.current.commonCards.worldDeck
  /** p1, at Ancient City, holds the Vial and 2 faceup secrets, and no
    * adviser; the site holds no card. */
  private def bare: ReadyGame =
    Table.start.pawn(p1, "Ancient City").relic(p1, vial)
      .secrets(p1, faceUp = 2).ready

  test("the cost is one secret placed on the Vial and one burnt"):
    val t = accepted(use(staged, power, source))
    assert(awaits(t, CrystalVial.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(vial), Tokens(0, 1))
    assertEquals(secretsOf(after(t)), (0, 0))

  test("it offers the actor's advisers in either orientation and the cards " +
      "at the actor's site, and nobody else's"):
    val ready = Table.from(staged).adviser(actor, elders)
      .adviser(actor, faith, facedown = true).denizen(inn, at = "Ancient City")
      .unchecked
    val t = accepted(use(ready, power, source))
    assertEquals(offered(t, actor).map(_.toSet), Some(Set(
      "denizen" -> held.value, "denizen" -> elders.value, "vision" -> faith.value) ++
      siteCards(ready)))

  test("an adviser is buried at the bottom of the world deck, and its favor " +
      "and secrets return"):
    val ready = Table.from(staged).tokens(held, favor = 2, secrets = 1)
      .unchecked
    val suit = Suit.Order // Wrestlers' printed suit
    val t = accepted(use(ready, power, source))
    val done = accepted(answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))))
    assert(!Look(after(done)).player(actor).advisers.exists(_.id == held))
    assertEquals(worldDeck(after(done)).last, held)
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 2)
    assertEquals(secretsOf(after(done)), (0, 1))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("a Vision adviser can be buried"):
    val ready = Table.from(staged).adviser(actor, faith, facedown = true)
      .unchecked
    val t = accepted(use(ready, power, source))
    val done = accepted(answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Vision(faith))))
    assert(!Look(after(done)).player(actor).advisers.exists(_.id == faith))
    assertEquals(worldDeck(after(done)).last, faith)

  test("a denizen at the site is buried with its favor and secrets returned"):
    val ready = Table.from(staged).denizen(inn, at = "Ancient City")
      .tokens(inn, favor = 1, secrets = 1).unchecked
    val suit = Suit.Hearth // Wayside Inn's printed suit
    val t = accepted(use(ready, power, source))
    val done = accepted(answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(inn))))
    assert(!siteCards(after(done)).contains("denizen" -> inn.value))
    assertEquals(worldDeck(after(done)).last, inn)
    assertEquals(after(done).banks.favor(suit), ready.banks.favor(suit) + 1)
    assertEquals(secretsOf(after(done)), (0, 1))

  test("the edifice at the site is buried whichever face it shows"):
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val edifice = CatalogNames.edifice("Marble Fountains")
      val ready = Table.from(staged).edifice(edifice, side, at = "Ancient City")
        .unchecked
      val t = accepted(use(ready, power, source))
      val done = accepted(answer(t, actor, CrystalVial.decisionId,
        pick(DecisionOptionRef.Edifice(edifice))))
      assert(!siteCards(after(done)).contains("edifice" -> edifice.value),
        side.toString)
      assertEquals(after(done).game.current.commonCards.edificeDeck.last,
        edifice, side.toString)
    }

  test("only the acting player answers, with an offered card"):
    val t = accepted(use(staged, power, source))
    assert(answer(t, other, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))).isLeft)
    assert(answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(theirs))).isLeft)

  test("with no candidate the cost is paid and nothing else happens"):
    val ready = bare
    assertEquals(siteCards(ready), Vector.empty)
    val t = accepted(use(ready, power, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).tokensOn(vial), Tokens(0, 1))
    assertEquals(secretsOf(after(t)), (0, 0))

  test("it needs two faceup secrets"):
    Vector((1, 0), (1, 5)).foreach { case (up, down) =>
      val ready = Table.from(staged).secrets(actor, up, down).unchecked
      assertEquals(usableNow(ready), Vector.empty, s"$up up, $down down")
      assert(use(ready, power, source).isLeft)
    }

  test("it writes the card it buried in place of the generic line"):
    val t = accepted(use(staged, power, source))
    val done = accepted(answer(t, actor, CrystalVial.decisionId,
      pick(DecisionOptionRef.Denizen(held))))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} buried ${held.value}.", covers = true)))

  test("with no candidate nothing is written"):
    assertEquals(NoteText.said(power, accepted(use(bare, power, source))
      .events),
      Vector.empty)
