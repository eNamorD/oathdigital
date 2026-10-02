package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class MemoryOfNatureSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val nature = CatalogNames.denizen("Memory of Nature")
  private val source = DecisionOptionRef.Denizen(nature)
  private val power = MemoryOfNature.forCatalog(catalog)
  // Three Beast denizens, and a Beast edifice on its intact face.
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val faeMerchant = CatalogNames.denizen("Fae Merchant")
  private val secondChance = CatalogNames.denizen("Second Chance")
  private val beastEdifice = CatalogNames.edifice("Hallowed Spring")
  /** p1's Act, p1 at Ancient City and p2 at Broken Peaks. */
  private val acting = Table.start.turn(p1, Phase.Act)
    .pawn(p1, "Ancient City").pawn(p2, "Broken Peaks")

  /** `table` with the banks holding exactly `favor`. */
  private def withBanks(table: Table, favor: (Suit, Int)*): Table =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(table) {
      case (staged, (suit, n)) => staged.bankFavor(suit, n) }

  private def bank(ready: ReadyGame, suit: Suit) =
    ready.banks.favor.getOrElse(suit, 0)
  private def rows(amounts: (Suit, Int)*) = DecisionAnswer.DistributeAnswer(
    amounts.toVector.map { case (suit, n) =>
      DistributeAmount(DecisionOptionRef.FavorBank(suit), n) })

  test("it places a secret and moves one favor per Beast card, an " +
      "edifice on either face included"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(errandBoy, at = "Ancient City")
      .edifice(beastEdifice, EdificeSide.Intact, at = "Broken Peaks"),
      Suit.Arcane -> 5).ready
    val t = accepted(use(ready, power, source))
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(nature), Tokens(0, 1))
    assertEquals((bank(end, Suit.Arcane), bank(end, Suit.Beast)), (3, 2))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 2 favor to the Beast bank.", covers = false)))

  test("it counts itself at a site, and a facedown card not at all"):
    val ready = withBanks(acting.denizen(nature, at = "Ancient City")
      .denizen(errandBoy, at = "Broken Peaks", facedown = true),
      Suit.Arcane -> 5).ready
    val t = accepted(use(ready, power, source))
    assertEquals(bank(after(t), Suit.Beast), 1)

  test("two banks holding 4 favor in all ask for the split of 2, one per " +
      "Beast card"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(errandBoy, at = "Ancient City")
      .denizen(faeMerchant, at = "Broken Peaks"),
      Suit.Arcane -> 2, Suit.Order -> 2).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, MemoryOfNature.decisionId),
      parked.parkedDecision(t.state).toString)
    val done = accepted(answer(t, p1, MemoryOfNature.decisionId,
      rows(Suit.Arcane -> 1, Suit.Order -> 1)))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Vector(Suit.Arcane, Suit.Order, Suit.Beast)
      .map(bank(after(done), _)), Vector(1, 1, 2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("two banks holding 2 favor in all give all they hold, unasked, when " +
      "3 Beast cards ask for 3, and the Beast bank's own favor stays"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(errandBoy, at = "Ancient City")
      .denizen(faeMerchant, at = "Ancient City")
      .denizen(secondChance, at = "Broken Peaks"),
      Suit.Arcane -> 1, Suit.Order -> 1, Suit.Beast -> 4).ready
    val t = accepted(use(ready, power, source))
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Vector(Suit.Arcane, Suit.Order, Suit.Beast)
      .map(bank(after(t), _)), Vector(0, 0, 6))

  test("with no Beast card nothing moves, and the line says so"):
    val ready = withBanks(acting.adviser(p1, nature), Suit.Arcane -> 3).ready
    val t = accepted(use(ready, power, source))
    assertEquals(bank(after(t), Suit.Arcane), 3)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", "No favor moved to the Beast bank.", covers = false)))

  test("a ruined Beast edifice counts as well as an intact one"):
    val ready = withBanks(acting.adviser(p1, nature)
      .edifice(beastEdifice, EdificeSide.Ruined, at = "Broken Peaks"),
      Suit.Arcane -> 5).ready
    val t = accepted(use(ready, power, source))
    assertEquals(bank(after(t), Suit.Beast), 1)
