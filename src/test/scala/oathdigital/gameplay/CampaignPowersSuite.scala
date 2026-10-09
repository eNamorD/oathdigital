package oathdigital.gameplay

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.powers.travel.{NarrowPassSitePower,
  TravelSitePowers}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
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
  import PassMap.{ancientCity, brokenPeaks, buriedGiant, deepWoods,
    desolateShore, pass}
  private def withPawnAt(site: SiteId, map: MapState = PassMap.map)
      : ReadyGame =
    PassMap.withPawnAt(initial, actor, site, map)

  private val passPower = TravelSitePowers.all.collectFirst {
    case power: NarrowPassSitePower => power }.getOrElse(
      fail("the Narrow Pass power is not registered"))
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
    val advanced = ProcedureWalker.advance(ready, tree, None, powers)
    val pending = Outcomes.parked(advanced).tree
    val decisions = ProcedureWalker.openDecisions(ready, tree, pending, powers)
    present(decisions.headOption, "the targets decision").query match
      case many: DecisionQuery.ChooseMany => many.options.filter(_.available)
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
    val without = PassMap.map.copy(sites = PassMap.map.sites - pass,
      provinces = PassMap.map.provinces.filterNot(_ == pass))
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

  /** The sites the parked decision shows unavailable with the Pass's note. */
  private def passReasons(ready: ReadyGame): Vector[(DecisionOptionRef, String)] =
    val tree = targets(candidates.map(siteOption))
    val pending = Outcomes.parked(ProcedureWalker.advance(ready, tree, None,
      WalkerPowers(Vector(passPower)))).tree
    ProcedureWalker.parkedDecide(ready, tree, pending,
      WalkerPowers(Vector(passPower))).map(_.query).toVector.flatMap:
      case many: DecisionQuery.ChooseMany => many.options.collect {
        case DecisionOption.Unavailable(option,
            OptionReason.Noted(power, note)) if power == passPower.id =>
          option.ref -> note.key
      }
      case _ => Vector.empty

  test("each site the Pass hides keeps its reason and writes no line"):
    // Two sites share the Pass's region: Buried Giant and Deep Woods.
    assertEquals(passNotes(withPawnAt(ancientCity)), Vector.empty)
    assertEquals(passReasons(withPawnAt(ancientCity)), Vector(
      siteOption(buriedGiant).ref -> "no-target",
      siteOption(deepWoods).ref -> "no-target"))

  test("a Pass that hides nothing writes nothing"):
    assertEquals(passNotes(withPawnAt(buriedGiant)), Vector.empty)
