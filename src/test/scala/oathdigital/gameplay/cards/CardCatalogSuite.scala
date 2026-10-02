package oathdigital.gameplay.cards

import oathdigital.catalog.{AdviserOnly, ExecutableCatalog, Locked,
  PrintedPower, PrintsPowers, SiteOnly}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.model.{CardId, EdificeId, PowerId, RelicId}

/** The printed-corpus checks `validate-component-catalog.py` ran on the JSON,
  * now run on the Scala catalog. Suits, edifice faces and the single Grand
  * Scepter are types, so they need no check. Restrictions are free-mixing
  * traits, so a test pins them.
  */
class CardCatalogSuite extends munit.FunSuite:
  private val catalog = NewFoundations.catalog
  private val printing: Vector[PrintsPowers] =
    catalog.denizens ++ catalog.relics ++ catalog.legacies ++
      catalog.edifices.flatMap(e => Vector(e.intact, e.ruined))
  private val powers = printing.flatMap(_.powers)
  private val symbols = Set("attack-die", "defense-die", "favor",
    "favor-burnt", "hollow-sword", "round-die", "secret", "secret-burnt",
    "shield", "skull", "suit-arcane", "suit-beast", "suit-discord",
    "suit-hearth", "suit-nomad", "suit-order", "sword")
  private val symbol = "\\[([a-z-]+)\\]".r

  test("the catalog holds every printed component"):
    assertEquals(Vector(catalog.denizens.size, catalog.relics.size,
      catalog.edifices.size, catalog.legacies.size, catalog.sites.size),
      Vector(255, 48, 30, 36, 24))

  test("printed ids are the expected sets"):
    assertEquals(catalog.denizens.map(_.id.value).toSet,
      ((1 to 258).toSet -- Set(94, 110, 174)).map(_.toString))
    assertEquals(catalog.relics.map(_.id.value).toSet,
      (1 to 47).map(n => f"R$n%02d").toSet + "grand-scepter")
    assertEquals(catalog.edifices.map(_.id.value).toSet,
      (1 to 30).map(n => f"E$n%02d").toSet)
    assertEquals(catalog.legacies.map(_.id.value).toSet,
      (1 to 36).map(n => f"L$n%02d").toSet)
    assert(catalog.sites.forall(_.id.value.startsWith("site:")))

  test("card ids are unique across kinds"):
    val ids = catalog.denizens.map(_.id.value) ++ catalog.relics.map(_.id.value) ++
      catalog.edifices.map(_.id.value) ++ catalog.legacies.map(_.id.value) ++
      catalog.sites.map(_.id.value)
    assertEquals(ids.diff(ids.distinct), Vector.empty)

  test("power ids and site handlers are unique"):
    val ids = powers.map(_.id.value) ++ catalog.sites.flatMap(_.handlers)
    assertEquals(ids.diff(ids.distinct), Vector.empty)

  test("printed text is non-blank and uses only the symbol vocabulary"):
    powers.foreach { p =>
      assert(p.text.trim.nonEmpty, p.id)
      val unknown = symbol.findAllMatchIn(p.rulesText).map(_.group(1))
        .filterNot(symbols).toVector
      assertEquals(unknown, Vector.empty, p.id)
      assert(!p.rulesText.exists("©®�".contains(_)), p.id)
    }

  test("restrictions match the printed corpus"):
    def kind(card: AnyRef): String = card match
      case _: (Locked & AdviserOnly) => "locked-adviser-only"
      case _: Locked => "locked-only"
      case _: (SiteOnly & AdviserOnly) => "site-and-adviser"
      case _: SiteOnly => "site-only"
      case _: AdviserOnly => "adviser-only"
      case _ => "unrestricted"
    val counts = catalog.denizens.groupBy(kind).view.mapValues(_.size).toMap
    assertEquals(counts, Map("unrestricted" -> 133, "site-only" -> 51,
      "adviser-only" -> 40, "locked-adviser-only" -> 31))
    assert(catalog.edifices.forall(e => e.intact.isInstanceOf[Locked] &&
      !e.ruined.isInstanceOf[Locked] && !e.ruined.isInstanceOf[SiteOnly] &&
      !e.ruined.isInstanceOf[AdviserOnly]))
    assert(catalog.edifices.forall(e => !e.intact.isInstanceOf[SiteOnly] &&
      !e.intact.isInstanceOf[AdviserOnly]))

  test("a site has Forge requirements exactly when it has three slots"):
    catalog.sites.foreach(s =>
      assertEquals(s.forgeRequirements.nonEmpty, s.capacity == 3, s.id))

  test("printed numbers are non-negative"):
    assert(catalog.relics.forall(r => r.value >= 0 && r.defense >= 0))
    assert(catalog.sites.forall(s => s.defense >= 0 && s.capacity >= 0 &&
      s.relicSlots >= 0 && s.recoverDifficulty.forall(_ >= 0)))

  test("every registered card power is printed in the catalog"):
    val printed = (powers.map(_.id) ++
      catalog.sites.flatMap(_.handlers).map(PowerId(_))).toSet
    val cardPrefixes = Vector("denizen.", "relic.", "edifice.", "legacy.", "site.")
    // Take Wealth is a standing Wake rule of the game, not a printed power.
    val gameRules = Set(PowerId("site.take-wealth"))
    val registered = (WalkerPowerCatalog.default(catalog).powers.map(_.id) ++
      PhasePowerCatalog.default(catalog).powers.map(_.id))
      .filter(id => cardPrefixes.exists(id.value.startsWith))
      .filterNot(gameRules)
    assertEquals(registered.filterNot(printed).distinct, Vector.empty)

  test("a card leaves the holding files once it is implemented"):
    val registered = (WalkerPowerCatalog.default(catalog).powers.map(_.id) ++
      PhasePowerCatalog.default(catalog).powers.map(_.id)).toSet
    // The Grand Scepter and the Hall of Ministers are implemented as
    // operation restrictions, not as registered powers.
    val restrictions = Set[CardId](RelicId("grand-scepter"), EdificeId("E16"))
    def held(card: AnyRef): Boolean =
      card.getClass.getPackageName == "oathdigital.catalog.holding"
    def implemented(id: CardId, printed: Vector[PrintedPower]): Boolean =
      restrictions(id) || printed.exists(power => registered(power.id))
    val misplaced =
      catalog.denizens.collect { case card
          if held(card) == implemented(card.id, card.powers) => card.name } ++
        catalog.relics.collect { case card
          if held(card) == implemented(card.id, card.powers) => card.name } ++
        catalog.edifices.collect { case card if held(card) ==
            implemented(card.id, card.intact.powers ++ card.ruined.powers) =>
          card.intact.name }
    assertEquals(misplaced, Vector.empty)

  test("denizen powers are registered whichever denizens a catalog lists"):
    def denizenPowers(listed: ExecutableCatalog): Set[PowerId] =
      (WalkerPowerCatalog.default(listed).powers.map(_.id) ++
        PhasePowerCatalog.default(listed).powers.map(_.id))
        .filter(_.value.startsWith("denizen.")).toSet
    assertEquals(denizenPowers(catalog.copy(denizens = Vector.empty)),
      denizenPowers(catalog))
