package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.present

class RuleSourceIndexSuite extends munit.FunSuite:
  /** A board with a source of every category the rule-source index names:
    * Sticky Fire facedown at Broken Peaks, the ruined Hiding Place at Deep
    * Woods, and a facedown Wrestlers advising p1. */
  private val board: ReadyGame = Table.start
    .relicAt("Sticky Fire", at = "Broken Peaks")
    .edifice("Hiding Place", EdificeSide.Ruined, at = "Deep Woods")
    .adviser(p1, "Wrestlers", facedown = true)
    .ready

  test("the source index lists a printed site, a facedown source, an " +
      "edifice and an adviser, each once, in a stable order"):
    val ready = board
    val ancientCity = CatalogNames.site("Ancient City")
    val facts = RuleSourceIndex.enumerate(catalog, ready)
    val printed = present(facts.collectFirst {
      case value @ IndexedRuleSource(RuleSourceRef.Site(id), _, _, _)
          if id == ancientCity => value
    }, "Ancient City among the sources")
    assertEquals(printed.powerIds, present(
      catalog.sites.find(_.id == ancientCity), "Ancient City").handlers)
    assert(facts.exists(_.face == RuleSourceFace.FaceDown))
    assertEquals(facts.collect { case IndexedRuleSource(
      edifice: RuleSourceRef.Edifice, _, _, _) => edifice }.size, 1)
    assertEquals(facts.collect { case IndexedRuleSource(
      adviser: RuleSourceRef.Adviser, _, _, _) => adviser }.size, 1)
    assertEquals(facts, RuleSourceIndex.enumerate(catalog, ready))
    assertEquals(facts.map(_.source.stableKey).distinct.size, facts.size)

  test("site relics retain site identity, orientation, and declared handlers"):
    val ready = board
    val siteId = CatalogNames.site("Broken Peaks")
    val relic = CatalogNames.relic("Sticky Fire")
    val indexed = present(RuleSourceIndex.enumerate(catalog, ready).find(
      _.source == RuleSourceRef.SiteRelic(siteId, relic)),
      "Sticky Fire's source")
    val printedRelic = present(catalog.relics.find(_.id.value == relic.value),
      "Sticky Fire in the catalog")
    assertEquals(indexed.source.stableKey,
      s"site-relic:${siteId.value}:${relic.value}")
    assertEquals(indexed.powerIds, printedRelic.powers.map(power => power.id))
    // The board lays Sticky Fire facedown.
    assertEquals(indexed.face, RuleSourceFace.FaceDown)

  test("both banners expose faces, holdings, and exact synthetic handlers"):
    val base = board
    val changed = Table.from(base)
      .peoplesFavor(Some(p1), 3, face = PeoplesFavorFace.GrandCouncil)
      .darkestSecret(Some(p2), 2, face = DarkestSecretFace.Festival).ready
    val banners = RuleSourceIndex.enumerate(catalog, changed).filter(
      _.source.isInstanceOf[RuleSourceRef.Banner])
    assertEquals(banners.map(_.source.stableKey),
      Vector("banner:peoples-favor", "banner:darkest-secret"))
    assertEquals(banners.map(_.face),
      Vector(RuleSourceFace.GrandCouncil, RuleSourceFace.Festival))
    assertEquals(banners.map(_.state), Vector(
      RuleSourceState.Banner(Some(p1), 3),
      RuleSourceState.Banner(Some(p2), 2)))
    assertEquals(banners.map(_.powerIds), Vector(
      Vector(PowerId("banner.peoples-favor.grand-council")),
      Vector(PowerId("banner.darkest-secret.festival"))))

  test("all six Foundations expose ordered identities, faces, and state"):
    val base = board
    val arbiter = CatalogNames.legacy("Arbiter")
    val ironHand = CatalogNames.legacy("Iron Hand")
    // No Table step sets foundations or legacies.
    val altered = base.updateCampaign(_.copy(foundations =
      base.game.campaign.foundations.updated(FoundationNumber.III,
        FoundationState(FoundationFace.Altered, Set(arbiter, ironHand)))))
    val foundations = RuleSourceIndex.enumerate(catalog, altered).filter(
      _.source.isInstanceOf[RuleSourceRef.Foundation])
    assertEquals(foundations.map(_.source.stableKey),
      FoundationNumber.all.map(number => s"foundation:${number.value}"))
    assertEquals(foundations.map(_.face), Vector(
      RuleSourceFace.Normal, RuleSourceFace.Normal, RuleSourceFace.Altered,
      RuleSourceFace.Normal, RuleSourceFace.Normal, RuleSourceFace.Normal))
    assertEquals(foundations(2).powerIds, Vector(PowerId("foundation.altered")))
    // Sorted by id: Iron Hand is L01, Arbiter L23.
    assertEquals(foundations(2).state, RuleSourceState.Foundation(
      Vector(ironHand, arbiter)))
    assert(foundations.zipWithIndex.filterNot(_._2 == 2).forall {
      case (source, _) => source.powerIds.isEmpty &&
        source.state == RuleSourceState.Foundation(Vector.empty)
    })

  test("legacy inventory remains declared and lineage-qualified"):
    val base = board
    val lineageId = base.game.campaign.lineages.keys.toVector.sortBy(_.value).head
    val legacyDefinition = catalog.legacies.head
    val legacy = LegacyState(LegacyId(legacyDefinition.id.value), active = false)
    val lineage = base.game.campaign.lineages(lineageId)
    // No Table step sets foundations or legacies.
    val changed = base.updateCampaign(_.copy(lineages = base.game.campaign.lineages.updated(
        lineageId, lineage.copy(legacies = Vector(legacy)))))
    val indexed = present(RuleSourceIndex.enumerate(catalog, changed).find(
      _.source == RuleSourceRef.Legacy(lineageId, legacy.id)), "the legacy")
    assertEquals(indexed.source.stableKey,
      s"legacy:${lineageId.value}:${legacy.id.value}")
    assertEquals(indexed.powerIds, legacyDefinition.powers.map(_.id))
    assertEquals(indexed.face, RuleSourceFace.Inactive)
