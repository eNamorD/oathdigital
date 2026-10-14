package oathdigital.gameplay

import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Pins the catalog flags the first powers batch depends on. A modifier is
  * catalogued `persistent: false` and a persistent rule `persistent: true`.
  * When Played, ACTION, WAKE, REST and battle-plan powers are not listed: the
  * flag does not decide how they activate.
  */
class PowerKindsCatalogSuite extends munit.FunSuite:
  private val modifiers = Vector("denizen.augury", "relic.truthful-harp",
    "denizen.tents", "denizen.forest-paths", "relic.cup-of-plenty",
    "denizen.rowdy-pub", "relic.dragonskin-drum", "denizen.relic-worship",
    "denizen.knights-errant", "denizen.catacombs", "denizen.wild-cry",
    "denizen.welcoming-party", "denizen.animal-playmates", "denizen.birdsong",
    "denizen.royal-stables", "denizen.moving-market",
    "denizen.news-from-alfar", "denizen.old-fast-steed",
    "denizen.awaited-return", "denizen.mounted-library", "denizen.defame",
    "denizen.special-envoy", "denizen.golem-legions",
    "denizen.map-library", "denizen.skilled-merchants",
    "denizen.pressgangs", "denizen.old-songs", "denizen.small-friends",
    "denizen.friendly-familiar")
  private val persistentRules = Vector("denizen.toll-roads",
    "denizen.grasping-vines", "relic.circlet-of-command", "denizen.gossip",
    "denizen.league-treaty", "denizen.gleaming-armor", "edifice.e28.intact",
    "edifice.e28.ruined", "denizen.secret-police", "edifice.e08.intact",
    "denizen.book-binders", "denizen.forgotten-vault", "denizen.sealing-ward",
    "denizen.herald", "denizen.diplomat", "denizen.curfew",
    "denizen.forced-labor", "denizen.way-station", "denizen.vow-of-wandering",
    "denizen.marriage", "relic.lost-tapestry", "denizen.family-wagon",
    "denizen.marsh-spirit", "denizen.beast-tamer", "denizen.true-names",
    "denizen.vow-of-wisdom", "denizen.vow-of-union.campaign",
    "denizen.vow-of-union.restriction", "denizen.vow-of-silence",
    "denizen.vow-of-division", "relic.brass-army.restriction",
    "relic.secret-testament")

  private def flag(id: String): Option[Boolean] =
    catalog.printedPower(PowerId(id)).map(_.persistent)

  test("resolution follows the flag"):
    // A missing id would fall back to PlayerSelected, so every id must be
    // catalogued for the check below to mean anything.
    modifiers.foreach(id => assertEquals(flag(id), Some(false), id))
    persistentRules.foreach(id => assertEquals(flag(id), Some(true), id))
    modifiers.foreach(id => assertEquals(
      CatalogResolution.of(catalog, PowerId(id)), PowerResolution.PlayerSelected, id))
    persistentRules.foreach(id => assertEquals(
      CatalogResolution.of(catalog, PowerId(id)), PowerResolution.Automatic, id))

  test("Dazzle is a When Played power whatever its flag: it must fire on the " +
      "play, so it does not derive its resolution from the flag"):
    assertEquals(flag("denizen.dazzle"), Some(false))
    assertEquals(oathdigital.gameplay.powers.whenplayed.Dazzle
      .forCatalog(catalog).resolution, PowerResolution.Automatic)

  test("Vow of Obedience's rule is automatic whatever its flag: the flag is " +
      "false because of its REST"):
    assertEquals(flag("denizen.vow-of-obedience"), Some(false))
    assertEquals(oathdigital.gameplay.powers.cardplay.VowOfObedience
      .resolution, PowerResolution.Automatic)

  test("Pied Piper's limit is automatic whatever its flag: the flag is " +
      "false because of its ACTION"):
    assertEquals(flag("denizen.pied-piper"), Some(false))
    assertEquals(oathdigital.gameplay.powers.action.PiedPiper.resolution,
      PowerResolution.Automatic)

  test("Chaos Cult is a persistent rule and fires on every title change"):
    assertEquals(flag("denizen.chaos-cult"), Some(true))
    assertEquals(oathdigital.gameplay.powers.title.ChaosCult
      .resolution, PowerResolution.Automatic)

  test("Tyrant's rule is automatic whatever its flag: it is a must, not a " +
      "modifier"):
    assertEquals(flag("denizen.tyrant"), Some(false))
    assertEquals(oathdigital.gameplay.powers.travel.Tyrant.resolution,
      PowerResolution.Automatic)

  test("Hospitality's rule is automatic whatever its flag: it is a must, " +
      "not a modifier"):
    assertEquals(flag("denizen.hospitality"), Some(false))
    assertEquals(oathdigital.gameplay.powers.travel.Hospitality
      .forCatalog(catalog).resolution, PowerResolution.Automatic)

  test("Keep's rule is automatic whatever its flag: it is a must, not a " +
      "modifier"):
    assertEquals(flag("denizen.keep"), Some(false))
    assertEquals(oathdigital.gameplay.powers.campaign.KeepRule.resolution,
      PowerResolution.Automatic)

  test("Vow of Beastkin's rule is automatic whatever its flag"):
    assertEquals(flag("denizen.vow-of-beastkin"), Some(false))
    assertEquals(oathdigital.gameplay.powers.economy.VowOfBeastkin
      .forCatalog(catalog).resolution, PowerResolution.Automatic)

  test("Vow of Wandering's Muster power is automatic whatever its flag"):
    assertEquals(flag("denizen.vow-of-wandering.muster"), Some(false))
    assertEquals(oathdigital.gameplay.powers.economy.VowOfWanderingMuster
      .resolution, PowerResolution.Automatic)

  test("Vow of Poverty's rule is automatic whatever its flag: the flag is " +
      "false because of its REST"):
    assertEquals(flag("denizen.vow-of-poverty"), Some(false))
    assertEquals(oathdigital.gameplay.powers.economy.VowOfPoverty.resolution,
      PowerResolution.Automatic)
