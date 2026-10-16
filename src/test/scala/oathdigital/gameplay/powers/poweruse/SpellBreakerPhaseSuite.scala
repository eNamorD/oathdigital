package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powers.action.{RovingTerror, WardOfSilence}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Spell Breaker on the phase path (catalog batch 8, slice 10): the Act
  * menu shows a refused ACTION askable of the ruler, or unavailable, and
  * its start is refused until a grant is remembered. */
class SpellBreakerPhaseSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val breaker = CatalogNames.denizen("Spell Breaker")
  private val terror = CatalogNames.denizen("Roving Terror")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val terrorRef = DecisionOptionRef.Denizen(terror)
  private val power = RovingTerror.forCatalog(catalog)
  private val covers = ConsentKey(SpellBreaker.id, p1, "power.use",
    terrorRef)

  /** p1's Act at Ancient City beside Roving Terror, with Wrestlers at
    * Broken Peaks to replace, and Spell Breaker at Deep Woods, which
    * `ruled` gives its ruler: p2 by default. */
  private def terrorBesideBreaker(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(terror, at = home).denizen(wrestlers, at = peaks)
      .denizen(breaker, at = woods)).ready

  private def remembering(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(covers -> answer.by(p2)),
      turn = Some(ConsentTurn.of(ready))))

  private def terrorOption(ready: ReadyGame)(using munit.Location)
      : DecisionOption = MenuPlay.menuOptionFor(catalog, ready,
    ActionRef.UsePower(power.id), Vector(terrorRef))

  test("an enemy's secret-costing ACTION is askable of Spell Breaker's " +
      "ruler"):
    terrorOption(terrorBesideBreaker()) match
      case DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
        assertEquals(ask.power, SpellBreaker.id)
        assertEquals(ask.asks, Vector(ConsentAsk(p2,
          Vector(SpellBreaker.id))))
        assertEquals(ask.covers, Vector(covers))
      case other => fail(s"expected an askable option, got $other")

  test("its start is refused while the refusal stands"):
    assert(use(terrorBesideBreaker(), power, terrorRef).isLeft)

  test("a grant remembered this turn opens the ACTION"):
    val ready = remembering(terrorBesideBreaker(), ConsentAnswer.Granted)
    assert(terrorOption(ready).available)
    assert(awaits(accepted(use(ready, power, terrorRef)),
      RovingTerror.decisionId))

  test("under bandits the ACTION shows Spell Breaker's reason"):
    assertEquals(terrorOption(terrorBesideBreaker(_.bandits(woods, 2))),
      DecisionOption.Unavailable(DecisionOption.Denizen(terrorRef),
        OptionReason.Noted(SpellBreaker.id,
          SpellBreaker.plain(PowerSourceRef.Card(breaker)))))

  test("the ruler uses it freely"):
    assert(terrorOption(terrorBesideBreaker(_.warbandsAt(woods, p1, 1)))
      .available)

  test("Ward of Silence pays X secrets, so it is refused too"):
    assert(WardOfSilence.costsSecret)

  test("the askable row reads Spell Breaker's consent reason"):
    val parked = OathTransition(Table.from(terrorBesideBreaker())
      .phaseMenuParked.state, Vector.empty)
    val row = present(queryOf(parked, p1).toVector.flatMap(_.offeredOptions)
      .find(_.id == terror.value), "the Roving Terror row")
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Spell Breaker: Cannot use powers that " +
      "cost secrets without ruler's consent."))

  test("an ACTION p1 cannot pay for keeps its own reason: nobody is asked"):
    val broke = Table.from(terrorBesideBreaker()).secrets(p1, faceUp = 0)
      .ready
    val option = terrorOption(broke)
    assert(!option.available && !option.askable, option.toString)
