package oathdigital.gameplay

import oathdigital.gameplay.powers.{SelectedModifier, WalkerPowerCatalog}
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** The catalog's `persistent` flag against the resolution every registered
  * walker power declares. A modifier is catalogued `persistent: false` and
  * resolves player-selected; a persistent rule is catalogued `persistent:
  * true` and resolves automatic. Both read from the registered powers
  * themselves, so a power added to `WalkerPowerCatalog` is checked without
  * being named here. When Played, ACTION, WAKE, REST and battle-plan powers
  * declare `Automatic` whatever their flag: the flag does not decide how they
  * activate, and the named cases below pin the ones that read as exceptions.
  */
class PowerKindsCatalogSuite extends munit.FunSuite:
  private val registered = WalkerPowerCatalog.default(catalog).powers

  private def flag(id: String): Option[Boolean] =
    catalog.printedPower(PowerId(id)).map(_.persistent)

  test("every selected modifier is catalogued as not persistent"):
    val modifiers = registered.collect { case m: SelectedModifier => m }
    modifiers.foreach(m => assertEquals(flag(m.id.value), Some(false),
      m.id.value))

  test("Catacombs is the one player-selected power that is not a " +
      "SelectedModifier, and is catalogued as not persistent"):
    // Recover offers it through its own selection window, so it derives its
    // resolution from the flag without being a `SelectedModifier`.
    val selected = registered.filter(_.resolution ==
      PowerResolution.PlayerSelected).filterNot(_.isInstanceOf[SelectedModifier])
    assertEquals(selected.map(_.id), Vector(CatacombsContribution.id))
    assertEquals(flag(CatacombsContribution.id.value), Some(false))

  test("every other registered power resolves automatically, so a persistent " +
      "rule catalogued false would show up here"):
    val offered = registered.filterNot(_.isInstanceOf[SelectedModifier])
      .filterNot(_.id == CatacombsContribution.id)
      .filterNot(_.resolution == PowerResolution.Automatic)
    assertEquals(offered.map(_.id.value), Vector.empty)

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
