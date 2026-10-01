package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

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
    .denizen(beast, at = Table.homeOf(p1))

  /** Musters from Errand Boy, selecting nothing. */
  private def muster(ready: ReadyGame)
      : Either[OathViolation, (OathTransition, ReadyGame)] = for
    started <- rules.startWalker(Ready(ready), ActionRef.Muster, p1, Vector.empty)
    done <- rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(beast)))
  yield (started.copy(events = started.events ++ done.events),
    done.state.asInstanceOf[Ready].value)

  test("its holder's Muster places a faceup secret on the card instead of " +
      "a favor, without being selected"):
    val ready = initiated().ready
    val (transition, result) = muster(ready).toOption.get
    assertEquals(Look(result).tokensOn(beast), Tokens(0, 1))
    assertEquals(Look(result).favor(p1), 1)
    assertEquals(Look(result).faceUpSecrets(p1), 0)
    assertEquals(Look(result).supply(p1), 6)
    assertEquals(Look(result).warbands(p1), 3 + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("it is a rule, never offered as a modifier"):
    assert(!rules.offerableWalkerPowers(initiated().ready, p1,
      ActionRef.Muster).toOption.get.map(_.id).contains(InitiationRite.id))

  test("with no faceup secret its holder cannot Muster"):
    val hidden = initiated().secrets(p1, faceUp = 0, faceDown = 1)
    assert(muster(hidden.ready).isLeft)
    // The same board without the rule musters with its favor.
    val plain = Table.start.denizen(beast, at = Table.homeOf(p1))
      .secrets(p1, faceUp = 0, faceDown = 1).ready
    assert(muster(plain).isRight)

  test("a facedown Initiation Rite does nothing"):
    val (_, result) = muster(initiated(facedown = true).ready).toOption.get
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    assertEquals(Look(result).faceUpSecrets(p1), 1)

  test("a Trade is unaffected: a Trade for secrets still places a favor"):
    val ready = initiated().favor(p1, 2).ready
    val result = (for
      started <- rules.startWalker(Ready(ready), ActionRef.Trade, p1,
        Vector.empty, Vector(DecisionOptionRef.Button("secret")))
      done <- rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(beast)))
    yield done.state.asInstanceOf[Ready].value).toOption.get
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    assertEquals(Look(result).favor(p1), 0)
