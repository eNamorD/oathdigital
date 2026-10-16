package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Spell Breaker (card 162): "Enemies of Spell Breaker's ruler cannot use
  * powers that cost any [secret] or [secret-burnt]." (catalog batch 8,
  * slice 10). This part tests the restriction alone; the paths that read
  * it are tested in the suites after it. */
class SpellBreakerSuite extends munit.FunSuite:
  private val breaker = CatalogNames.denizen("Spell Breaker")
  private val woods = CatalogNames.site("Deep Woods")
  private val terror = CatalogNames.denizen("Roving Terror")

  /** p1's Act, with Spell Breaker at Deep Woods, which `ruled` gives its
    * ruler: p2's warband by default. */
  private def breakerAtDeepWoods(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).denizen(breaker, at = woods))
      .ready

  private def secretUse(by: PlayerId): PowerUse = PowerUse(by,
    PowerId("denizen.roving-terror"), Some(terror), persistent = false,
    costsSecret = true)

  test("it refuses an enemy of its ruler a power that costs a secret, " +
      "waivable by the ruler"):
    val refusal = SpellBreaker.refusal(breakerAtDeepWoods(), secretUse(p1))
    assertEquals(refusal.map(_.power), Some(SpellBreaker.id))
    assertEquals(refusal.flatMap(_.reason.waiver).flatMap(_.granter),
      Some(p2))
    assertEquals(refusal.flatMap(_.reason.waiver).map(_.covers),
      Some(ConsentKey(SpellBreaker.id, p1, "power.use",
        DecisionOptionRef.Denizen(terror))))

  test("its ruler, and a power that costs no secret, are not refused"):
    assertEquals(SpellBreaker.refusal(breakerAtDeepWoods(), secretUse(p2)),
      None)
    assertEquals(SpellBreaker.refusal(breakerAtDeepWoods(),
      secretUse(p1).copy(costsSecret = false)), None)

  test("under bandits every player is refused, and nobody may consent"):
    val refusal = SpellBreaker.refusal(
      breakerAtDeepWoods(_.bandits(woods, 2)), secretUse(p1))
    assertEquals(refusal.map(_.power), Some(SpellBreaker.id))
    assertEquals(refusal.flatMap(_.reason.waiver), None)

  test("at an unruled site it refuses nobody"):
    assertEquals(SpellBreaker.refusal(breakerAtDeepWoods(identity),
      secretUse(p1)), None)

  test("the consent grid lists it, granted by its ruler"):
    val powers = ConsentPowers.default(catalog)
    assertEquals(powers.granter(breakerAtDeepWoods(), SpellBreaker.id),
      Some(p2))
    assertEquals(powers.source(SpellBreaker.id),
      Some(PowerSourceRef.Card(breaker)))
    assertEquals(powers.granter(breakerAtDeepWoods(_.bandits(woods, 2)),
      SpellBreaker.id), None)
