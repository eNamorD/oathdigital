package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** An option that states its price projects as the option it wraps, with the
  * price worded as details.
  */
class PricedOptionProjectionSuite extends munit.FunSuite:
  private val actor = p1
  private val site = CatalogNames.site("Ancient City")

  /** The options p1 is offered by a parked decision offering `options`. */
  private def projected(options: Vector[DecisionOption]) =
    val ready = Table.start.parkedWalker(ActionRef.Recover).ready
    val tree = Sequence(Decide("test.priced", actor,
      DecisionQuery.ChooseOne(options)))
    new WalkerDecisionProjector(catalog, new GamePresentationProjector(catalog),
      WalkerPowers.empty, (_, _, _, _, _) => Right(tree))
      .project(ScopedProjectionContext(ready, Some(actor)))
      .flatMap(_.query).getOrElse(fail("the decision must project"))
      .offeredOptions

  test("a priced option is the wrapped option with its price as details"):
    val plain = DecisionOption.Site(DecisionOptionRef.Site(site))
    val options = projected(Vector(plain, DecisionOption.Priced(plain,
      OptionPrice(favor = 1, secrets = 2, favorBurnt = 1, secretsBurnt = 1,
        warbands = 1))))
    assertEquals(options.map(o => (o.kind, o.id)), Vector.fill(2)(
      ("site", site.value)))
    assertEquals(options.head.details, Vector.empty[String])
    assertEquals(options(1).label, options.head.label)
    assertEquals(options(1).details, Vector("Cost: 1 favor", "Cost: 2 secrets",
      "Cost: 1 favor burnt", "Cost: 1 secret burnt", "Cost: sacrifice 1 warband"))

  test("a price words only what it costs, and pluralises"):
    assertEquals(PriceDetails.of(OptionPrice()), Vector.empty[String])
    assertEquals(PriceDetails.of(OptionPrice(secretsBurnt = 2, warbands = 3)),
      Vector("Cost: 2 secrets burnt", "Cost: sacrifice 3 warbands"))

  test("an amount's price words X secrets"):
    assertEquals(PriceDetails.of(OptionPrice(secretsX = true)),
      Vector("Cost: X secrets"))
