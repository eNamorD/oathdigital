package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class RovingTerrorSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val terror = CatalogNames.denizen("Roving Terror")
  private val source = DecisionOptionRef.Denizen(terror)
  private val power = RovingTerror.forCatalog(catalog)
  /** p1's pawn site, and two other sites in play. */
  private val home = CatalogNames.site("Ancient City")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** An edifice, locked while intact. */
  private val debate = CatalogNames.edifice("Hall of Debate")

  /** p1's Act at Ancient City beside Roving Terror, site-only, with the
    * start's 1 faceup secret. Errand Boy stands at Broken Peaks. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(terror, at = home)
    .denizen(errandBoy, at = peaks)

  /** The walk Roving Terror started and the walk after `ref` was chosen. */
  private def choose(ready: ReadyGame, ref: DecisionOptionRef): UsedAndAnswered =
    useAndAnswer(ready, power, source,
      RovingTerror.decisionId, ref)

  test("it places a secret and offers the faceup denizens and ruined " +
      "edifices at the other sites, not its own site's or an intact edifice"):
    val ready = staged.denizen(wrestlers, at = home)
      .edifice(debate, EdificeSide.Intact, at = giant).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, RovingTerror.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(terror), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> errandBoy.value)))

  test("the chosen denizen gets the standard discard, and Roving Terror " +
      "moves there with its secret"):
    val ready = staged.tokens(errandBoy, favor = 1, secrets = 1)
      .bankFavor(Suit.Beast, 2).ready
    val UsedAndAnswered(t, done) = choose(ready, DecisionOptionRef.Denizen(errandBoy))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(after(done))
    assertEquals(end.cardsAt(peaks), Vector[CardId](terror))
    assertEquals(end.cardsAt(home), Vector.empty[CardId])
    assertEquals(end.tokensOn(terror), Tokens(0, 1))
    assert(end.discardAfter(peaks).contains(errandBoy))
    // Errand Boy's favor goes back to the Beast bank, its secret to p1
    // facedown.
    assertEquals(end.bankFavor(Suit.Beast), 2 + 1)
    assertEquals(end.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Replaced ${errandBoy.value} at ${peaks.value}.",
      covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("a ruined edifice goes to the bottom of the edifice deck"):
    val ready = staged.edifice(debate, EdificeSide.Ruined, at = giant).ready
    val UsedAndAnswered(_, done) = choose(ready, DecisionOptionRef.Edifice(debate))
    val end = after(done)
    assertEquals(Look(end).cardsAt(giant), Vector[CardId](terror))
    assertEquals(end.game.current.commonCards.edificeDeck.lastOption,
      Some(debate))

  test("with nothing to replace it asks nothing, keeps its secret, and the " +
      "line says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(terror, at = home)
      .edifice(debate, EdificeSide.Intact, at = giant).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).cardsAt(home), Vector[CardId](terror))
    assertEquals(Look(after(done)).tokensOn(terror), Tokens(0, 1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No card could be replaced.", covers = true)))

  test("it is unusable without a secret"):
    val broke = staged.secrets(p1, 0).ready
    assert(!usableNow(broke).exists(_.power.id == RovingTerror.id))
    assertEquals(use(broke, power, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))
