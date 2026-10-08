package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class DefameSuite extends munit.FunSuite:
  private val defame = CatalogNames.denizen("Defame")
  private val power = Defame.forCatalog(catalog)
  private val modifiers = Vector(Defame.id)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Discord denizen to Trade with. */
  private val mercenaries = CatalogNames.denizen("Mercenaries")
  /** A plain Order denizen to Trade with instead. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = PowerRules.withWalkerPowers
  private val parked = PowerRules.parkedUnderWalkerPowers

  /** p1 at Ancient City beside Mercenaries and Wrestlers, holding Defame as
    * a faceup adviser, with 7 Supply and 1 faceup secret to pay a Trade for
    * favor. Every board holds 1 favor, and the Discord and Order banks hold
    * 3 favor each. */
  private def defameTable: Table = Table.start.pawn(p1, at = home)
    .denizen(mercenaries, at = home).denizen(wrestlers, at = home)
    .adviser(p1, defame).supply(p1, 7).secrets(p1, faceUp = 1)
    .favor(p1, 1).favor(p2, 1).favor(p3, 1)
    .bankFavor(Suit.Discord, 3).bankFavor(Suit.Order, 3)

  /** p1 Trades for favor with Mercenaries, having selected Defame, and is
    * asked whose favor to burn. */
  private def tradedForFavorWithMercenaries(ready: ReadyGame): OathTransition =
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      modifiers, Vector(DecisionOptionRef.Button("favor"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(mercenaries))))
    asked.copy(events = started.events ++ asked.events)

  /** `asked`, answered with `ref`; the events run from the Trade's start. */
  private def answered(asked: OathTransition, ref: DecisionOptionRef)
      : OathTransition =
    val done = accepted(rules.resolveWalker(asked.state, p1, Defame.decisionId,
      ChooseOneAnswer(ref)))
    done.copy(events = asked.events ++ done.events)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("after trading with a Discord card, the chosen player burns 1 favor " +
      "from their board"):
    val ready = defameTable.ready
    val asked = tradedForFavorWithMercenaries(ready)
    parked.assertParked(asked.state, ActionRef.Trade, Defame.decisionId, p1)
    // Every board holds favor, so Skip is not offered.
    assertEquals(PhasePowerHarness.offered(asked, p1), Some(Vector(
      "player" -> p1.value, "player" -> p2.value, "player" -> p3.value)))
    val done = answered(asked, DecisionOptionRef.Player(p2))
    val end = Outcomes.ready(done.state)
    assertEquals(Look(end).favor(p2), Look(ready).favor(p2) - 1)
    assertEquals(said(done.events), Vector(NoteText.Said("burned",
      s"Burned 1 favor from ${p2.value}'s board.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), end)
    assertWireRoundTrips(done.events)

  test("Skip is offered when a player has no favor, and burns nothing"):
    val ready = defameTable.favor(p3, 0).ready
    val asked = tradedForFavorWithMercenaries(ready)
    assertEquals(PhasePowerHarness.offered(asked, p1), Some(Vector(
      "player" -> p1.value, "player" -> p2.value, "button" -> "skip")))
    val done = answered(asked, Defame.skip)
    assertEquals(Look(Outcomes.ready(done.state)).favor(p2),
      Look(ready).favor(p2))
    assertEquals(said(done.events), Vector.empty)

  test("a Trade with a card of another suit, or one after which nobody holds " +
      "favor, asks nothing"):
    val otherSuit = EconomyActions.trade(rules, defameTable.ready, p1,
      modifiers, "favor", DecisionOptionRef.Denizen(wrestlers))
    // A Trade for secrets spends p1's 2 favor, and the others hold none.
    val nobodyHolds = EconomyActions.trade(rules,
      defameTable.favor(p1, 2).favor(p2, 0).favor(p3, 0).ready, p1, modifiers,
      "secret", DecisionOptionRef.Denizen(mercenaries))
    Vector(otherSuit, nobodyHolds).foreach { done =>
      assertEquals(parked.parkedDecision(Ready(done.result)), None)
      assertEquals(said(done.journal.events), Vector.empty)
    }
