package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.{PowerRuntime, RuleSourceFace, RuleSourceIndex}
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table

/** The source index lists each banner face's powers, and the reviewed
  * catalog still audits every source the index lists.
  */
class BannerFaceSourcesSuite extends munit.FunSuite:
  import PowerFixture.{actor, quietTable}

  private def darkest(ready: ReadyGame) = RuleSourceIndex.enumerate(catalog,
    ready).filter(_.source == RuleSourceRef.Banner(Banner.DarkestSecret.key))

  test("the Wandering Flame face lists its two powers, and Festival its own"):
    val flame = darkest(quietTable).head
    assertEquals(flame.face, RuleSourceFace.WanderingFlame)
    assertEquals(flame.powerIds, Vector(WanderingFlameMove.id,
      WanderingFlamePlace.id))
    val festival = darkest(Table.from(quietTable).darkestSecret(Some(actor), 1,
      DarkestSecretFace.Festival).unchecked)
      .head
    assertEquals(festival.handlerIds, Vector("banner.darkest-secret.festival"))

  test("the reviewed catalog audits the banner powers, so options resolve " +
      "for a game whose banner is on that face"):
    assertEquals(PowerRuntime.options(catalog, quietTable, PowerFixture.actor,
      ActionKind.Travel), Right(Vector.empty))
    assertEquals(PowerRuntime.options(catalog,
      Table.from(quietTable).darkestSecret(Some(actor), 1).unchecked,
      PowerFixture.actor, ActionKind.Travel), Right(Vector.empty))
