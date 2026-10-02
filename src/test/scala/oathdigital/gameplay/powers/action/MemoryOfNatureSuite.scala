package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class MemoryOfNatureSuite extends munit.FunSuite:
  import TargetsFixture._

  private val nature = CatalogNames.denizen("Memory of Nature")
  private val source = DecisionOptionRef.Denizen(nature)
  private val power = MemoryOfNature.forCatalog(catalog)
  private val beasts = SearchFixture.denizensOf(Suit.Beast)
    .filterNot(_ == nature)
  private val beastEdifice =
    EdificeId(catalog.edifices.find(_.suit == Suit.Beast).get.id.value)
  private val acting = Table.start.turn(p1, Phase.Act)

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
      .denizen(beasts(0), at = Table.homeOf(p1))
      .edifice(beastEdifice, EdificeSide.Intact, at = Table.homeOf(p2)),
      Suit.Arcane -> 5).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(nature), Tokens(0, 1))
    assertEquals((bank(end, Suit.Arcane), bank(end, Suit.Beast)), (3, 2))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 2 favor to the Beast bank.", covers = false)))

  test("it counts itself at a site, and a facedown card not at all"):
    val ready = withBanks(acting.denizen(nature, at = Table.homeOf(p1))
      .denizen(beasts(0), at = Table.homeOf(p2), facedown = true),
      Suit.Arcane -> 5).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(bank(after(t), Suit.Beast), 1)

  test("several banks holding more than X ask for the split"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(beasts(0), at = Table.homeOf(p1))
      .denizen(beasts(1), at = Table.homeOf(p2)),
      Suit.Arcane -> 2, Suit.Order -> 2).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, MemoryOfNature.decisionId),
      parked.parkedDecision(t.state).toString)
    val done = answer(t, p1, MemoryOfNature.decisionId,
      rows(Suit.Arcane -> 1, Suit.Order -> 1)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Vector(Suit.Arcane, Suit.Order, Suit.Beast)
      .map(bank(after(done), _)), Vector(1, 1, 2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the other banks holding X or less give all they hold, unasked, " +
      "and the Beast bank's own favor stays"):
    val ready = withBanks(acting.adviser(p1, nature)
      .denizen(beasts(0), at = Table.homeOf(p1))
      .denizen(beasts(1), at = Table.homeOf(p1))
      .denizen(beasts(2), at = Table.homeOf(p2)),
      Suit.Arcane -> 1, Suit.Order -> 1, Suit.Beast -> 4).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Vector(Suit.Arcane, Suit.Order, Suit.Beast)
      .map(bank(after(t), _)), Vector(0, 0, 6))

  test("with no Beast card nothing moves, and the line says so"):
    val ready = withBanks(acting.adviser(p1, nature), Suit.Arcane -> 3).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(bank(after(t), Suit.Arcane), 3)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", "No favor moved to the Beast bank.", covers = false)))

  test("a ruined Beast edifice counts as well as an intact one"):
    val ready = withBanks(acting.adviser(p1, nature)
      .edifice(beastEdifice, EdificeSide.Ruined, at = Table.homeOf(p2)),
      Suit.Arcane -> 5).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(bank(after(t), Suit.Beast), 1)
