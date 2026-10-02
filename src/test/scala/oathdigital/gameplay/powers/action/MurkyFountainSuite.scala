package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class MurkyFountainSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val fountain = CatalogNames.edifice("Marble Fountains")
  private val source = DecisionOptionRef.Edifice(fountain)
  /** p1's Act at Ancient City with `supply` Supply. The edifice stands on
    * `side` at Ancient City or, when `pawnAtEdifice` is false, at Broken
    * Peaks, which p1 rules with one warband, so that only the pawn
    * condition fails.
    */
  private def staged(side: EdificeSide = EdificeSide.Ruined,
      pawnAtEdifice: Boolean = true, supply: Int): ReadyGame =
    val table = Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .supply(actor, supply)
    (if pawnAtEdifice then table.edifice(fountain, side, at = "Ancient City")
    else table.edifice(fountain, side, at = "Broken Peaks")
      .warbandsAt("Broken Peaks", actor, 1)).ready

  test("at its site it places a secret and gains Supply equal to the total"):
    val rules0 = rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields))
    // 1 Supply and 3 shields make 4.
    val ready0 = staged(supply = 1)
    val done = accepted(use(rules0, ready0, MurkyFountain.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(4))
    assertEquals(Look(end).tokensOn(fountain), Tokens(0, 1))
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("a Doubler doubles the shields"):
    val rules0 = rules(defenseDice(DefenseDieFace.TwoShields,
      DefenseDieFace.Doubler))
    // 1 Supply and 2 doubled shields make 5.
    val end = ready(accepted(use(rules0, staged(supply = 1), MurkyFountain.id,
      source)).state)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(5))

  test("the gain is clamped at the track maximum"):
    val rules0 = rules(defenseDice(DefenseDieFace.TwoShields,
      DefenseDieFace.TwoShields))
    val end = ready(accepted(use(rules0, staged(supply = 5), MurkyFountain.id,
      source)).state)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(7))

  test("a total of zero ends the Act phase without gaining Supply"):
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank))
    // 1 Supply, unchanged.
    val ready0 = staged(supply = 1)
    val done = accepted(use(rules0, ready0, MurkyFountain.id, source))
    val end = ready(done.state)
    walkerParked.assertResumed(done.state, Phase.Rest, actor)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(1))
    assertEquals(Look(end).tokensOn(fountain), Tokens(0, 1))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("with the pawn elsewhere the cost is paid, nothing rolls and the phase stays"):
    // 1 Supply, unchanged.
    val away = staged(pawnAtEdifice = false, supply = 1)
    assert(usableIds(away).contains(MurkyFountain.id))
    val done = accepted(use(rules(), away, MurkyFountain.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).tokensOn(fountain), Tokens(0, 1))
    assertEquals(allSecrets(end), allSecrets(away) - 1)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(1))
    walkerParked.assertResumed(done.state, Phase.Act, actor)

  test("it is unusable without a faceup secret"):
    val noSecret = Table.from(staged(supply = 1)).secrets(actor, 0).unchecked
    assert(!usableIds(noSecret).contains(MurkyFountain.id))
    assert(use(rules(), noSecret, MurkyFountain.id, source).isLeft)

  test("the intact face is Marble Fountains and offers no Murky Fountain"):
    val intact = staged(side = EdificeSide.Intact, supply = 1)
    assert(!usableIds(intact).contains(MurkyFountain.id))
    assert(use(rules(), intact, MurkyFountain.id, source).isLeft)

  test("it writes its roll and the Supply it gained"):
    val done = accepted(use(rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields)), staged(
        supply = 1), MurkyFountain.id, source))
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 3",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 3 Supply.", covers = false)))

  test("a total of zero writes that the Act phase ended"):
    val done = accepted(use(rules(defenseDice(DefenseDieFace.Blank,
      DefenseDieFace.Blank)), staged(supply = 1), MurkyFountain.id, source))
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 0",
        covers = true),
      NoteText.Said("ended", s"${actor.value}'s Act phase ended.", covers = false)))

  test("with the pawn elsewhere it writes only that"):
    val done = accepted(use(rules(), staged(pawnAtEdifice = false, supply = 1),
      MurkyFountain.id, source))
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said("used.away", s"${actor.value} was not at its site.",
        covers = false)))
