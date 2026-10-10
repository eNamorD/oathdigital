package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1

class MountedLibrarySuite extends munit.FunSuite:
  private val library = CatalogNames.denizen("Mounted Library")
  private val power = MountedLibrary.forCatalog(catalog)
  private val modifiers = Vector(MountedLibrary.id)
  private val home = CatalogNames.site("Ancient City")
  /** A Nomad adviser, so a Trade with Mounted Library gains 1 secret, or 1
    * more favor. */
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  /** A plain Order denizen to Trade with instead. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Mounted Library and Wrestlers, holding Horse
    * Archers faceup, 2 favor and 1 faceup secret. The Nomad and Order banks
    * hold 3 favor each. */
  private def besideTheLibrary: Table = Table.start.pawn(p1, at = home)
    .denizen(library, at = home).denizen(wrestlers, at = home)
    .adviser(p1, horseArchers).favor(p1, 2).secrets(p1, faceUp = 1)
    .bankFavor(Suit.Nomad, 3).bankFavor(Suit.Order, 3)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def secrets(look: Look): Int =
    look.faceUpSecrets(p1) + look.faceDownSecrets(p1)

  test("trading with it for secrets doubles them, then it is discarded and " +
      "its favor goes back to the Nomad bank"):
    val ready = besideTheLibrary.ready
    val done = EconomyActions.trade(rules, ready, p1, modifiers, "secret",
      DecisionOptionRef.Denizen(library))
    val end = Look(done.result)
    // The 1 secret p1 holds, 1 for Horse Archers, and 1 more.
    assertEquals(secrets(end), 3)
    assertEquals(end.cardsAt(home), Vector[CardId](wrestlers))
    assert(end.discardAfter(home).contains(library))
    // The Trade placed 1 favor on it; the discard returns it.
    assertEquals(end.bankFavor(Suit.Nomad), 3 + 1)
    assertEquals(said(done.journal.events), Vector(
      NoteText.Said("doubled", "Gained 1 more secret.", covers = true),
      NoteText.Said("discarded", "Discarded after the Trade.", covers = true)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("trading with it for favor gains no more, and its discard returns the " +
      "secret paid onto it facedown"):
    val ready = besideTheLibrary.ready
    val done = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(library))
    val end = Look(done.result)
    // 1 Nomad favor, and 1 more for Horse Archers.
    assertEquals(end.favor(p1), 2 + 2)
    assertEquals(secrets(end), 1)
    assertEquals(end.faceDownSecrets(p1), 1)
    assert(end.discardAfter(home).contains(library))
    assertEquals(said(done.journal.events), Vector(NoteText.Said("discarded",
      "Discarded after the Trade.", covers = true)))

  test("selected for a Trade with another card, it is still discarded; " +
      "unselected, it stays"):
    val ready = besideTheLibrary.ready
    val selected = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    assert(Look(selected.result).discardAfter(home).contains(library))
    val unselected = EconomyActions.trade(rules, ready, p1, Vector.empty,
      "favor", DecisionOptionRef.Denizen(wrestlers))
    assert(Look(unselected.result).cardsAt(home).contains(library))
    assertEquals(said(unselected.journal.events), Vector.empty)

  test("both its rewrites fold Late, after every other Trade modifier"):
    val phases = Vector(PowerWindow.TradeGain,
      PowerWindow.TradeActionEligibility).flatMap(window =>
      power.contributions(window).collect {
        case transform: Transform => transform.phase })
    assertEquals(phases, Vector(FoldPhase.Late, FoldPhase.Late))
    assertEquals(power.priority, 0)
