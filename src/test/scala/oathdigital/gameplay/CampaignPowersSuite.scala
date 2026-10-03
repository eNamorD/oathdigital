package oathdigital.gameplay

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.powers.travel.{NarrowPassSitePower,
  TravelSitePowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Unwrap.present

class CampaignPowersSuite extends munit.FunSuite:
  private val initial = Table.start.ready
  private val actor: PlayerId = Table.p1
  private val other: PlayerId = Table.p2

  // ---- Vow of Peace ------------------------------------------------------
  private val eligibility =
    Sequence(Vector.empty, Some(PowerWindow.CampaignActionEligibility))
  private val vowPowers =
    WalkerPowers(Vector(VowOfPeaceContribution))

  private def holding(orientation: Orientation, holder: PlayerId): ReadyGame =
    Table.start.adviser(holder, "Vow of Peace",
      facedown = orientation == Orientation.FaceDown).ready

  private def vowViolations(ready: ReadyGame) =
    ProcedureWalker.restrictionViolations(eligibility, vowPowers, ready, actor)

  test("a faceup Vow of Peace blocks its holder's Campaign at eligibility"):
    assertEquals(vowViolations(holding(Orientation.FaceUp, actor)), Vector(
      OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning")))

  test("a facedown Vow of Peace, or another player's, blocks nothing"):
    assertEquals(vowViolations(holding(Orientation.FaceDown, actor)),
      Vector.empty[OathViolation])
    assertEquals(vowViolations(holding(Orientation.FaceUp, other)),
      Vector.empty[OathViolation])
    assertEquals(vowViolations(initial), Vector.empty[OathViolation])

  // ---- Narrow Pass -------------------------------------------------------
  private val pass = CatalogNames.site("Narrow Pass")
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val deepWoods = CatalogNames.site("Deep Woods")
  private val desolateShore = CatalogNames.site("Desolate Shore")
  private val dunes = CatalogNames.site("Dunes")
  private val fairIsle = CatalogNames.site("Fair Isle")
  /** Cradle: Ancient City, Broken Peaks. Provinces: the Pass, Buried Giant,
    * Deep Woods. Hinterland: Desolate Shore, Dunes, Fair Isle. Each site
    * holds its printed bandits. No Table step lays out regions, so the map
    * is built here.
    */
  private val passMap =
    val cradle = Vector(ancientCity, brokenPeaks)
    val provinces = Vector(pass, buriedGiant, deepWoods)
    val hinterland = Vector(desolateShore, dunes, fairIsle)
    val states = (cradle ++ provinces ++ hinterland).map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if definition.capacity == 0 then SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    MapState(cradle, provinces, hinterland, states)
  private def withPawnAt(site: SiteId, map: MapState = passMap): ReadyGame =
    Table.from(initial.updateCurrent(_.copy(map = map))).pawn(actor, at = site)
      .ready

  private val passPower = TravelSitePowers.all.collectFirst {
    case power: NarrowPassSitePower => power }.get
  private def siteOption(id: SiteId) =
    DecisionOption.Site(DecisionOptionRef.Site(id))
  private val candidates = Vector(pass, buriedGiant, deepWoods, brokenPeaks,
    desolateShore)
  private def targets(options: Vector[DecisionOption]) =
    val query = DecisionQuery.ChooseMany(0, options.size, options,
      Some("Choose targets"))
    Sequence(Vector[Operation](Decide("campaign.targets", actor, query,
      window = Some(PowerWindow.CampaignTargetSelection))))

  private def offered(ready: ReadyGame,
      options: Vector[DecisionOption] = candidates.map(siteOption))
      : Vector[DecisionOption] =
    val tree = targets(options)
    val powers = WalkerPowers(Vector(passPower))
    val pending = Outcomes.parked(ProcedureWalker.advance(ready, tree, None,
      powers)).tree
    present(ProcedureWalker.openDecisions(ready, tree, pending, powers)
      .headOption, "the targets decision").query match
      case many: DecisionQuery.ChooseMany => many.options
      case other => fail(s"expected a choose-many, got $other")

  test("Pass keeps its own site targetable and removes the other sites in " +
      "its region"):
    // From Ancient City, Buried Giant and Deep Woods share the Pass's region.
    assertEquals(offered(withPawnAt(ancientCity)).map(_.ref),
      Vector(pass, brokenPeaks, desolateShore).map(DecisionOptionRef.Site(_)))

  test("a pawn already inside the Pass's region is not blocked"):
    assertEquals(offered(withPawnAt(buriedGiant)).map(_.ref),
      candidates.map(DecisionOptionRef.Site(_)))

  test("the ruler of the Pass may target every site in its region"):
    val ruled = Table.from(withPawnAt(ancientCity)).warbandsAt(pass, actor, 1)
      .ready
    assertEquals(offered(ruled).map(_.ref),
      candidates.map(DecisionOptionRef.Site(_)))

  test("options that are not sites are never filtered"):
    val relics = Vector("r1", "r2").map(id =>
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(id))))
    assertEquals(offered(withPawnAt(ancientCity), relics), relics)

  test("a Pass that is not in play forbids nothing"):
    val without = passMap.copy(sites = passMap.sites - pass,
      provinces = passMap.provinces.filterNot(_ == pass))
    val sameRegion = Vector(buriedGiant, deepWoods).map(siteOption)
    assertEquals(offered(withPawnAt(ancientCity, without), sameRegion),
      sameRegion)

  /** The notes the Pass writes when the walk parks on `options`. */
  private def passNotes(ready: ReadyGame,
      options: Vector[DecisionOption] = candidates.map(siteOption))
      : Vector[NoteText.Said] =
    val parked = Outcomes.parked(ProcedureWalker.advance(ready,
      targets(options), None, WalkerPowers(Vector(passPower))))
    NoteText.said(passPower.id, passPower.noteKeys, parked.events)

  test("each site the Pass hides writes the same line"):
    // Two sites share the Pass's region: Buried Giant and Deep Woods.
    assertEquals(passNotes(withPawnAt(ancientCity)), Vector.fill(2)(
      NoteText.Said("no-target",
        s"${actor.value} cannot target other sites in the region.",
        covers = false)))

  test("a Pass that hides nothing writes nothing"):
    assertEquals(passNotes(withPawnAt(buriedGiant)), Vector.empty)
