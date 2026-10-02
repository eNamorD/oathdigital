package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.Table

class RuleSourceIndexSuite extends munit.FunSuite:
  /** A board with a source of every category the rule-source index names:
    * Sticky Fire facedown at Broken Peaks, the ruined Hiding Place at Deep
    * Woods, and a facedown Wrestlers advising p1. */
  private val board: ReadyGame = Table.start
    .relicAt("Sticky Fire", at = "Broken Peaks")
    .edifice("Hiding Place", EdificeSide.Ruined, at = "Deep Woods")
    .adviser(Table.p1, "Wrestlers", facedown = true)
    .ready

  test("factual source index enumerates every source category deterministically"):
    val ready = board
    val facts = RuleSourceIndex.enumerate(catalog, ready)
    val printed = facts.collectFirst {
      case value @ IndexedRuleSource(RuleSourceRef.Site(id), _, _, _)
          if id == ready.game.current.map.inPlay.head => value
    }.get
    assertEquals(printed.powerIds,
      catalog.sites.find(_.id == ready.game.current.map.inPlay.head).get.handlers)
    assert(facts.exists(_.face == RuleSourceFace.FaceDown))
    assert(facts.exists(_.source.isInstanceOf[RuleSourceRef.Edifice]))
    assert(facts.exists(_.source.isInstanceOf[RuleSourceRef.Adviser]))
    assertEquals(facts, RuleSourceIndex.enumerate(catalog, ready))
    assertEquals(facts.map(_.source.stableKey).distinct.size, facts.size)

  test("site relics retain site identity, orientation, and declared handlers"):
    val ready = board
    val (siteId, relic) = ready.game.current.map.inPlay.iterator.flatMap(id =>
      ready.game.current.map.sites(id).relics.headOption.map(id -> _)).next()
    val indexed = RuleSourceIndex.enumerate(catalog, ready).find(
      _.source == RuleSourceRef.SiteRelic(siteId, relic.id)).get
    assertEquals(indexed.source.stableKey,
      s"site-relic:${siteId.value}:${relic.id.value}")
    assertEquals(indexed.handlerIds,
      catalog.relics.find(_.id.value == relic.id.value).get.powers.map(_.id.value))
    assertEquals(indexed.powerIds,
      catalog.relics.find(_.id.value == relic.id.value).get.powers.map(power =>
        power.id))
    assertEquals(indexed.face, relic.orientation match {
      case Orientation.FaceUp => RuleSourceFace.FaceUp
      case Orientation.FaceDown => RuleSourceFace.FaceDown
    })

  test("both banners expose faces, holdings, and exact synthetic handlers"):
    val base = board
    val changed = base.updateCurrent(_.copy(banners =
      BannersState(
        PeoplesFavorState(PeoplesFavorFace.GrandCouncil, Some(PlayerId("p1")), 3),
        DarkestSecretState(DarkestSecretFace.Festival, Some(PlayerId("p2")), 2))))
    val banners = RuleSourceIndex.enumerate(catalog, changed).filter(
      _.source.isInstanceOf[RuleSourceRef.Banner])
    assertEquals(banners.map(_.source.stableKey),
      Vector("banner:peoples-favor", "banner:darkest-secret"))
    assertEquals(banners.map(_.face),
      Vector(RuleSourceFace.GrandCouncil, RuleSourceFace.Festival))
    assertEquals(banners.map(_.state), Vector(
      RuleSourceState.Banner(Some(PlayerId("p1")), 3),
      RuleSourceState.Banner(Some(PlayerId("p2")), 2)))
    assertEquals(banners.map(_.handlerIds), Vector(
      Vector("banner.peoples-favor.grand-council"),
      Vector("banner.darkest-secret.festival")))

  test("all six Foundations expose ordered identities, faces, and state"):
    val base = board
    val altered = base.updateCampaign(_.copy(foundations = base.game.campaign.foundations.updated(
        FoundationNumber.III, FoundationState(FoundationFace.Altered,
          Set(LegacyId("L23"), LegacyId("L01"))))))
    val foundations = RuleSourceIndex.enumerate(catalog, altered).filter(
      _.source.isInstanceOf[RuleSourceRef.Foundation])
    assertEquals(foundations.map(_.source.stableKey),
      FoundationNumber.all.map(number => s"foundation:${number.value}"))
    assertEquals(foundations.map(_.face), Vector(
      RuleSourceFace.Normal, RuleSourceFace.Normal, RuleSourceFace.Altered,
      RuleSourceFace.Normal, RuleSourceFace.Normal, RuleSourceFace.Normal))
    assertEquals(foundations(2).handlerIds, Vector("foundation.altered"))
    assertEquals(foundations(2).state, RuleSourceState.Foundation(
      Vector(LegacyId("L01"), LegacyId("L23"))))
    assert(foundations.zipWithIndex.filterNot(_._2 == 2).forall {
      case (source, _) => source.handlerIds.isEmpty &&
        source.state == RuleSourceState.Foundation(Vector.empty)
    })

  test("legacy inventory remains declared and lineage-qualified"):
    val base = board
    val lineageId = base.game.campaign.lineages.keys.toVector.sortBy(_.value).head
    val legacyDefinition = catalog.legacies.head
    val legacy = LegacyState(LegacyId(legacyDefinition.id.value), active = false)
    val lineage = base.game.campaign.lineages(lineageId)
    val changed = base.updateCampaign(_.copy(lineages = base.game.campaign.lineages.updated(
        lineageId, lineage.copy(legacies = Vector(legacy)))))
    val indexed = RuleSourceIndex.enumerate(catalog, changed).find(
      _.source == RuleSourceRef.Legacy(lineageId, legacy.id)).get
    assertEquals(indexed.source.stableKey,
      s"legacy:${lineageId.value}:${legacy.id.value}")
    assertEquals(indexed.handlerIds, legacyDefinition.powers.map(_.id.value))
    assertEquals(indexed.face, RuleSourceFace.Inactive)
