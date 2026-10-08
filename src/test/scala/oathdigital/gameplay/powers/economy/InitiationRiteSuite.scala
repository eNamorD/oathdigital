package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class InitiationRiteSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val rite = CatalogNames.denizen("Initiation Rite")
  // A plain Beast card with no Muster or Trade power.
  private val beast = CatalogNames.denizen("Errand Boy")

  /** p1 holds Initiation Rite as an adviser, faceup unless `facedown`, and
    * stands at Ancient City with Errand Boy. p1 has the start's 1 favor,
    * 1 faceup secret, 3 warbands and 7 Supply. */
  private def initiated(facedown: Boolean = false): Table = Table.start
    .adviser(p1, rite, facedown = facedown)
    .pawn(p1, at = "Ancient City").denizen(beast, at = "Ancient City")
    .favor(p1, 1).secrets(p1, faceUp = 1).boardWarbands(p1, 3).supply(p1, 7)

  /** Musters from Errand Boy, selecting nothing. */
  private def muster(ready: ReadyGame): EconomyActions.Completed =
    EconomyActions.muster(rules, ready, p1, Vector.empty,
      DecisionOptionRef.Denizen(beast))

  /** Musters from Errand Boy, selecting nothing; a refusal comes back as the
    * violation. */
  private def musterFromBeast(ready: ReadyGame)
      : Either[OathViolation, OathTransition] =
    EconomyActions.attemptMuster(rules, ready, p1, Vector.empty,
      DecisionOptionRef.Denizen(beast))

  test("its holder's Muster places a faceup secret on the card instead of " +
      "a favor, without being selected"):
    val ready = initiated().ready
    val done = muster(ready)
    val result = done.result
    assertEquals(Look(result).tokensOn(beast), Tokens(0, 1))
    // The favor stays; the one faceup secret goes onto the card.
    assertEquals(Look(result).favor(p1), 1)
    assertEquals(Look(result).faceUpSecrets(p1), 0)
    // A Muster costs 1 Supply and gains 1 warband.
    assertEquals(Look(result).supply(p1), 7 - 1)
    assertEquals(Look(result).boardWarbands(p1), 3 + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      result)
    assertWireRoundTrips(done.journal.events)

  test("it is a rule, never offered as a modifier"):
    assert(!accepted(rules.offerableWalkerPowers(initiated().ready, p1,
      ActionRef.Muster)).map(_.id).contains(InitiationRite.id))

  test("with no faceup secret its holder cannot Muster"):
    val hidden = initiated().secrets(p1, faceUp = 0, faceDown = 1)
    assertEquals(musterFromBeast(hidden.ready).left.toOption,
      Some(OathViolation.CoreOperationRejected("insufficient-pieces",
        "player play area contains 0 of requested secrets")))
    // The same board without the rule musters with its favor.
    val plain = Table.start.pawn(p1, at = "Ancient City")
      .denizen(beast, at = "Ancient City")
      .secrets(p1, faceUp = 0, faceDown = 1).ready
    assertEquals(Look(Outcomes.ready(accepted(musterFromBeast(plain)).state))
      .tokensOn(beast), Tokens(1, 0))

  test("a facedown Initiation Rite does nothing"):
    val result = muster(initiated(facedown = true).ready).result
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    assertEquals(Look(result).faceUpSecrets(p1), 1)

  test("a Trade is unaffected: a Trade for secrets still places a favor"):
    val ready = initiated().favor(p1, 2).ready
    val result = EconomyActions.trade(rules, ready, p1, Vector.empty, "secret",
      DecisionOptionRef.Denizen(beast)).result
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    // A Trade for secrets costs 2 favor: p1's 2.
    assertEquals(Look(result).favor(p1), 0)
