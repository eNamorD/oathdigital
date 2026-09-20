package oathdigital.gameplay

import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Pins the catalog flags the first powers batch depends on. A modifier is
  * catalogued `persistent: false` and a persistent rule `persistent: true`.
  * When Played, ACTION, WAKE, REST and battle-plan powers are not listed: the
  * flag does not decide how they activate.
  */
class PowerKindsCatalogSuite extends munit.FunSuite {
  private val modifiers = Vector("denizen.augury", "relic.truthful-harp",
    "denizen.tents", "denizen.forest-paths", "relic.cup-of-plenty",
    "denizen.rowdy-pub", "relic.dragonskin-drum", "denizen.relic-worship",
    "denizen.knights-errant", "denizen.catacombs", "denizen.wild-cry",
    "denizen.welcoming-party")
  private val persistentRules = Vector("denizen.toll-roads",
    "denizen.grasping-vines", "relic.circlet-of-command", "denizen.gossip",
    "denizen.league-treaty", "denizen.gleaming-armor", "edifice.e28.intact",
    "edifice.e28.ruined")

  private def flag(id: String): Option[Boolean] =
    CatalogResolution.printed(catalog, PowerId(id)).map(_.persistent)

  test("every in-scope modifier is catalogued non-persistent") {
    modifiers.foreach(id => assertEquals(flag(id), Some(false), id))
  }

  test("every in-scope persistent rule is catalogued persistent") {
    persistentRules.foreach(id => assertEquals(flag(id), Some(true), id))
  }

  test("resolution follows the flag") {
    modifiers.foreach(id => assertEquals(
      CatalogResolution.of(catalog, PowerId(id)), PowerResolution.PlayerSelected, id))
    persistentRules.foreach(id => assertEquals(
      CatalogResolution.of(catalog, PowerId(id)), PowerResolution.Automatic, id))
  }

  test("Dazzle is a When Played power whatever its flag: it must fire on the " +
      "play, so it does not derive its resolution from the flag") {
    assertEquals(flag("denizen.dazzle"), Some(false))
    assertEquals(oathdigital.gameplay.powers.whenplayed.Dazzle
      .forCatalog(catalog).get.resolution, PowerResolution.Automatic)
  }
}
