package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class PlagueEnginesSuite extends munit.FunSuite:
  import TargetsFixture._

  private val engines = CatalogNames.denizen("Plague Engines")
  private val source = DecisionOptionRef.Denizen(engines)

  /** p1's Act holding Plague Engines with two secrets, the Arcane bank
    * empty. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .adviser(p1, engines).secrets(p1, 2).bankFavor(Suit.Arcane, 0)

  private def arcane(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Arcane, 0)

  test("each player puts 1 favor per site they rule into the Arcane bank, " +
      "the user first"):
    val ready = staged.favor(p1, 5).favor(p2, 3)
      .warbandsAt(Table.homeOf(p1), p1, 1).warbandsAt(Table.homeOf(p3), p1, 1)
      .warbandsAt(Table.homeOf(p2), p2, 1).ready
    val t = use(ready, PlagueEngines, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(engines), Tokens(0, 1))
    assertEquals(Vector(p1, p2, p3).map(Look(end).favor), Vector(3, 2, 1))
    assertEquals(arcane(end), 3)
    assertEquals(NoteText.said(PlagueEngines, t.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} put 2 favor into the Arcane bank.", covers = false),
      NoteText.Said(NoteKey.Used,
        s"${p2.value} put 1 favor into the Arcane bank.", covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("a player short of favor gives all they have"):
    val ready = staged.favor(p2, 1)
      .warbandsAt(Table.homeOf(p2), p2, 1).warbandsAt(Table.homeOf(p3), p2, 1)
      .ready
    val t = use(ready, PlagueEngines, source).toOption.get
    assertEquals((Look(after(t)).favor(p2), arcane(after(t))), (0, 1))
    assertEquals(NoteText.said(PlagueEngines, t.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p2.value} put 1 favor into the Arcane bank.", covers = false)))

  test("with no site ruled nothing moves, and the line says so"):
    val t = use(staged.ready, PlagueEngines, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(arcane(after(t)), 0)
    assertEquals(NoteText.said(PlagueEngines, t.events), Vector(
      NoteText.Said("used.none", "No player put favor into the Arcane bank.",
        covers = false)))
