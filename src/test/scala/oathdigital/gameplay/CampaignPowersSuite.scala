package oathdigital.gameplay

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.powers.travel.{NarrowPassSitePower, TravelSitePowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.Table

class CampaignPowersSuite extends munit.FunSuite:
  private val initial = Table.start.ready
  private val actor: PlayerId = initial.game.current.turn.activePlayer
  private val other: PlayerId =
    initial.game.current.players.map(_.player).find(_ != actor).get

  // ---- Vow of Peace ------------------------------------------------------
  private val vowCard = catalog.denizenWithPower(PowerId("denizen.vow-of-peace")).get
  private val eligibility =
    Sequence(Vector.empty, Some(PowerWindow.CampaignActionEligibility))
  private val vowPowers =
    WalkerPowers(Vector(VowOfPeaceContribution))

  private def holding(orientation: Orientation, holder: PlayerId): ReadyGame =
    initial.updateCurrent(current => current.copy(players = current.players.map(
      player => if player.player == holder then player.copy(advisers = Vector(
        DenizenState(DenizenId(vowCard.id.value), orientation, Tokens.empty)))
      else player)))

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
  private val pass = catalog.sites.find(_.handlers.contains(
    PowerId("site.narrow-pass.pass"))).get.id
  private val others = catalog.sites.map(_.id).filterNot(_ == pass)
  /** Cradle: o0, o1. Provinces: the Pass, o2, o3. Hinterland: o4, o5, o6. */
  private val ordered = Vector(others.head, others(1), pass, others(2),
    others(3), others(4), others(5), others(6))
  private val passMap =
    val states = ordered.map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if definition.capacity == 0 then SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    MapState(ordered.take(2), ordered.slice(2, 5), ordered.slice(5, 8), states)
  private def withPawnAt(site: SiteId, map: MapState = passMap): ReadyGame =
    initial.updateCurrent(current => current.copy(map = map,
      players = current.players.map(player =>
        if player.player == actor then player.copy(pawnSite = Some(site))
        else player)))

  private val passPower = TravelSitePowers.all.collectFirst {
    case power: NarrowPassSitePower => power }.get
  private def siteOption(id: SiteId) =
    DecisionOption.Site(DecisionOptionRef.Site(id))
  private val candidates = Vector(pass, ordered(3), ordered(4), ordered(1),
    ordered(5))
  private def targets(options: Vector[DecisionOption]) = Sequence(Vector[Operation](
    Decide("campaign.targets", actor, DecisionQuery.ChooseMany(0, options.size,
      options, Some("Choose targets")),
      window = Some(PowerWindow.CampaignTargetSelection))))

  private def offered(ready: ReadyGame,
      options: Vector[DecisionOption] = candidates.map(siteOption))
      : Vector[DecisionOption] =
    val tree = targets(options)
    val powers = WalkerPowers(Vector(passPower))
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    ProcedureWalker.openDecisions(ready, tree, pending, powers).head.query match
      case many: DecisionQuery.ChooseMany => many.options
      case other => fail(s"expected a choose-many, got $other")

  test("Pass keeps its own site targetable and removes the other sites in its region"):
    assertEquals(offered(withPawnAt(ordered.head)).map(_.ref),
      Vector(pass, ordered(1), ordered(5)).map(DecisionOptionRef.Site(_)))

  test("a pawn already inside the Pass's region is not blocked"):
    assertEquals(offered(withPawnAt(ordered(3))).map(_.ref),
      candidates.map(DecisionOptionRef.Site(_)))

  test("the ruler of the Pass may target every site in its region"):
    val lineage = initial.game.current.players.find(_.player == actor).get.lineage
    val ruled = passMap.copy(sites = passMap.sites.updated(pass,
      passMap.sites(pass).copy(forces = SiteForces.Occupied(
        ForceKind.Exile(lineage), 1))))
    assertEquals(offered(withPawnAt(ordered.head, ruled)).map(_.ref),
      candidates.map(DecisionOptionRef.Site(_)))

  test("options that are not sites are never filtered"):
    val relics = Vector("r1", "r2").map(id =>
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(id))))
    assertEquals(offered(withPawnAt(ordered.head), relics), relics)

  test("a Pass that is not in play forbids nothing"):
    val without = passMap.copy(sites = passMap.sites - pass,
      provinces = passMap.provinces.filterNot(_ == pass))
    assertEquals(offered(withPawnAt(ordered.head, without),
      Vector(ordered(3), ordered(4)).map(siteOption)),
      Vector(ordered(3), ordered(4)).map(siteOption))

  /** The notes the Pass writes when the walk parks on `options`. */
  private def passNotes(ready: ReadyGame,
      options: Vector[DecisionOption] = candidates.map(siteOption))
      : Vector[NoteText.Said] =
    val Right(WalkerOutcome.Parked(_, events)) = ProcedureWalker.advance(ready,
      targets(options), None, WalkerPowers(Vector(passPower))): @unchecked
    NoteText.said(passPower.id, passPower.noteKeys, events)

  test("each site the Pass hides writes the same line, which merges to one"):
    assertEquals(passNotes(withPawnAt(ordered.head)), Vector.fill(2)(
      NoteText.Said("no-target",
        s"${actor.value} cannot target other sites in the region.",
        covers = false)))

  test("a Pass that hides nothing writes nothing"):
    assertEquals(passNotes(withPawnAt(ordered(3))), Vector.empty)
