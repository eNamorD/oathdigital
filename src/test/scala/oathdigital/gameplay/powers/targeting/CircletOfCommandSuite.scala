package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture.{RaidBoard, raidBoard}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx}
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Unwrap.{accepted, as, present}
import oathdigital.testkit.Table.{p1, p2}

class CircletOfCommandSuite extends munit.FunSuite:
  import TargetingFixture._

  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))

  // ---- Raid ----

  /** The Raid board with the Circlet on the defender (or the attacker),
    * beside the relic and the banners the board already gives them, the Raid
    * chosen: the transition that parks on the Raid's targets.
    */
  private def raidKind(circletSide: Option[Orientation],
      attacker: Boolean = false): OathTransition =
    val b = raidBoard().board
    val holder = if attacker then b.actor else b.rival
    val held = circletSide.fold(b.ready)(side => Table.from(b.ready)
      .relic(holder, circlet, facedown = side == Orientation.FaceDown)
      .unchecked)
    val started = accepted(start(held, ActionRef.Campaign, b.actor))
    accepted(
      rules.resolveWalker(started.state, b.actor, CampaignIds.kind, raid))

  private def raidTargets(circletSide: Option[Orientation],
      attacker: Boolean = false): Vector[DecisionOptionRef] =
    optionsAt(raidKind(circletSide, attacker), ActionRef.Campaign)

  test("a Raid may not target the holder's other relics or banners, but may " +
      "target the Circlet"):
    val RaidBoard(b, relic) = raidBoard()
    assertEquals(raidTargets(None).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Relic(relic),
      DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(raidTargets(Some(Orientation.FaceUp)),
      Vector[DecisionOptionRef](DecisionOptionRef.Relic(circlet)))

  test("a facedown Circlet protects nothing"):
    val relic = raidBoard().relic
    assert(raidTargets(Some(Orientation.FaceDown))
      .contains(DecisionOptionRef.Relic(relic)))

  test("the holder's own Raid is not restricted by their Circlet"):
    // The defender holds no Circlet; the attacker does. The defender's things
    // stay targetable: the Circlet protects only its holder.
    val relic = raidBoard().relic
    assert(raidTargets(Some(Orientation.FaceUp), attacker = true)
      .contains(DecisionOptionRef.Relic(relic)))

  // ---- Challenge ----

  /** p1 challenges at Ancient City, where p2 stands holding People's Favor
    * and, on `circletSide`, the Circlet. */
  private def challenge(circletSide: Option[Orientation]): OathTransition =
    val held = circletSide.fold(challengeTable)(side => challengeTable
      .relic(p2, circlet, facedown = side == Orientation.FaceDown))
    accepted(start(held.ready, ActionRef.Challenge, p1))

  private def challengeBanners(circletSide: Option[Orientation])
      : Vector[DecisionOptionRef] =
    optionsAt(challenge(circletSide), ActionRef.Challenge)

  test("a Challenge may name any banner: the Circlet protects only a " +
      "Campaign's targets"):
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    assertEquals(challengeBanners(Some(Orientation.FaceUp)).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val crackedHorn = CatalogNames.relic("Cracked Horn")

  /** The game a Conspiracy walk ran on, the target options it parked on, and
    * its events. */
  private final case class ConspiracyWalk(ready: ReadyGame,
      options: Vector[DecisionOptionRef], events: Vector[OathEvent])

  /** p1 plays Conspiracy at Ancient City, where p2 stands holding Cracked
    * Horn, the Circlet on `circletSide` and the People's Favor. */
  private def conspiracyTargets(circletSide: Orientation): ConspiracyWalk =
    val ready = Table.start.pawn(p2, at = "Ancient City")
      .peoplesFavor(Some(p2), favor = 1).hand(p1, conspiracy)
      .relic(p2, crackedHorn)
      .relic(p2, circlet, facedown = circletSide == Orientation.FaceDown)
      .ready
    val hook = CardPlayedFaceup(conspiracy,
      RuleSourceRef.Adviser(p1, conspiracy))
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    val advanced = ProcedureWalker.advance(ready, hook, None, powers)
    val parked = Outcomes.parked(advanced)
    val decide = present(ProcedureWalker.parkedDecide(ready, hook, parked.tree,
      powers), "the target decision")
    ConspiracyWalk(ready,
      as[DecisionQuery.ChooseOne](decide.query).options.map(_.ref),
      parked.events)

  test("Conspiracy may take any of the holder's relics and banners"):
    val walk = conspiracyTargets(Orientation.FaceUp)
    assertEquals(Look(walk.ready).player(p2).relics.map(_.id),
      Vector(crackedHorn, circlet))
    assertEquals(walk.options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(p2, 0), DecisionOptionRef.RelicSlot(p2, 1),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))

  test("a facedown Circlet leaves every target open"):
    val walk = conspiracyTargets(Orientation.FaceDown)
    assertEquals(walk.options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(p2, 0), DecisionOptionRef.RelicSlot(p2, 1),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))

  test("the Circlet's rule ignores an option that is not a banner or a relic"):
    val power = CircletOfCommand.forCatalog(catalog)
    val ready = Table.start.relic(p1, circlet).ready
    val ctx = PowerCtx(ready, p1, power.source,
      PowerWindow.CampaignTargetSelection, Vector.empty,
      Decide("x", p1, DecisionQuery.ChooseOne(Vector.empty)))
    val restriction = as[OptionRestriction](
      power.contributions(PowerWindow.CampaignTargetSelection).head)
    assertEquals(restriction.fn(ctx, DecisionOptionRef.Site(SiteId("s"))), None)

  // ---- Lines ----

  private val power = CircletOfCommand.forCatalog(catalog)
  private def hidden(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the Raid targets the Circlet hides write no line"):
    // The relic, the People's Favor and the Darkest Secret are hidden, each
    // with its reason on hover.
    assertEquals(hidden(raidKind(Some(Orientation.FaceUp)).events),
      Vector.empty)
    assertEquals(hidden(raidKind(Some(Orientation.FaceDown)).events),
      Vector.empty)

  test("a Challenge writes no Circlet line"):
    assertEquals(hidden(challenge(Some(Orientation.FaceUp)).events),
      Vector.empty)

  test("a Conspiracy writes no Circlet line"):
    assertEquals(hidden(conspiracyTargets(Orientation.FaceUp).events),
      Vector.empty)
