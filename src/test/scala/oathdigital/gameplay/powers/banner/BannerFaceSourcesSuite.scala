package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.{PowerRuntime, RuleSourceFace, RuleSourceIndex}
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

/** The source index lists each banner face's powers, and the reviewed
  * catalog still audits every source the index lists.
  */
class BannerFaceSourcesSuite extends munit.FunSuite:
  import PowerFixture.quietTable

  private def darkest(ready: ReadyGame) = RuleSourceIndex.enumerate(catalog,
    ready).filter(_.source == RuleSourceRef.Banner(Banner.DarkestSecret.key))

  test("the Wandering Flame face lists its two powers, and Festival its own"):
    val flame = darkest(quietTable).head
    assertEquals(flame.face, RuleSourceFace.WanderingFlame)
    assertEquals(flame.powerIds, Vector(WanderingFlameMove.id,
      WanderingFlamePlace.id))
    val onFestival = Table.start.darkestSecret(Some(p1), 1,
      DarkestSecretFace.Festival).ready
    val festival = darkest(onFestival).head
    assertEquals(festival.handlerIds, Vector("banner.darkest-secret.festival"))

  test("Travel offers no option, whether the Banner of the Darkest Secret " +
      "is unheld or held on Wandering Flame"):
    assertEquals(PowerRuntime.options(catalog, quietTable, PowerFixture.actor,
      ActionKind.Travel), Right(Vector.empty))
    assertEquals(
      PowerRuntime.options(catalog,
      Table.start.darkestSecret(Some(p1), 1)
        .ready, PowerFixture.actor, ActionKind.Travel), Right(Vector.empty))
