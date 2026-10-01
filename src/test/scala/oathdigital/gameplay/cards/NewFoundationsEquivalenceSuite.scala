package oathdigital.gameplay.cards

import oathdigital.catalog._
import oathdigital.catalog.holding.TheGrandScepterCard
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.Suit

/** Temporary: the generated Scala catalog matches the JSON it was generated
  * from, field by field. Deleted with the loader once the server runs on the
  * Scala catalog (card classes slice 1, Task 3).
  */
class NewFoundationsEquivalenceSuite extends munit.FunSuite:
  private def restriction(card: AnyRef): CardRestrictions = card match
    case _: (Locked & AdviserOnly) => CardRestrictions.LockedAdviserOnly
    case _: Locked => CardRestrictions.Locked
    case _: SiteOnly => CardRestrictions.SiteOnly
    case _: AdviserOnly => CardRestrictions.AdviserOnly
    case _ => CardRestrictions.Unrestricted

  private def fromJson(powers: Vector[CatalogPower]) =
    powers.map(p => (p.id, p.persistent, p.rulesText))
  private def fromScala(powers: Vector[PrintedPower]) =
    powers.map(p => (p.id, p.persistent, p.rulesText))

  test("denizens match the JSON"):
    assertEquals(
      NewFoundations.denizens.sortBy(_.id.value).map(d => (d.id.value, d.name,
        d.suit, restriction(d), fromScala(d.powers))),
      catalog.denizens.map(d => (d.id.value, d.name, d.suit, d.restrictions,
        fromJson(d.powers))))

  test("relics match the JSON, and only The Grand Scepter is a scepter"):
    assertEquals(
      NewFoundations.relics.sortBy(_.id.value).map(r => (r.id.value, r.name,
        r.id == TheGrandScepterCard.id, r.value, r.defense,
        fromScala(r.powers))),
      catalog.relics.map(r => (r.id.value, r.name,
        r.role == RelicRole.GrandScepter, r.value, r.defense,
        fromJson(r.powers))))

  test("edifices match the JSON, face by face"):
    assertEquals(
      NewFoundations.edifices.sortBy(_.id.value).map(e => (e.id.value, e.suit,
        e.intact.name, restriction(e.intact), fromScala(e.intact.powers),
        e.ruined.name, restriction(e.ruined), fromScala(e.ruined.powers))),
      catalog.edifices.map(e => (e.id.value, e.suit,
        e.intact.name, e.intact.restrictions, fromJson(e.intact.powers),
        e.ruined.name, e.ruined.restrictions, fromJson(e.ruined.powers))))

  test("legacies match the JSON"):
    assertEquals(
      NewFoundations.legacies.sortBy(_.id.value).map(l =>
        (l.id.value, l.name, fromScala(l.powers))),
      catalog.legacies.map(l => (l.id.value, l.name, fromJson(l.powers))))

  test("sites match the JSON, with the homeland read from its handler"):
    def homeland(handlers: Vector[String]): Option[Suit] =
      handlers.collectFirst { case h if h.contains(".homeland-") =>
        Suit.fromKey(h.substring(h.indexOf(".homeland-") + 10)).get }
    assertEquals(
      NewFoundations.sites.sortBy(_.id.value).map(s => (s.id, s.name,
        s.defense, s.capacity, s.relicSlots, s.recoverDifficulty,
        s.startingResources, s.forgeRequirements, s.homeland, s.handlers)),
      catalog.sites.map(s => (s.id, s.name, s.defense, s.capacity,
        s.relicSlots, s.recoverDifficulty, s.startingResources,
        s.forgeRequirements, homeland(s.handlers), s.handlers)))
