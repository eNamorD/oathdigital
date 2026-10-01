package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.CampaignFixture.raidBoard
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{CardStaging, NoteText, PowerFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Table
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
    val (b, _) = raidBoard()
    val held = circletSide.fold(b.ready)(side =>
      holds(b.ready, if attacker then b.actor else b.other, circlet, side))
    val started = start(held, ActionRef.Campaign, b.actor).toOption.get
    rules.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get

  private def raidTargets(circletSide: Option[Orientation],
      attacker: Boolean = false): Vector[DecisionOptionRef] =
    optionsAt(raidKind(circletSide, attacker), ActionRef.Campaign)

  test("a Raid may not target the holder's other relics or banners, but may " +
      "target the Circlet"):
    val (b, relic) = raidBoard()
    assertEquals(raidTargets(None).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Relic(relic), DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(raidTargets(Some(Orientation.FaceUp)),
      Vector[DecisionOptionRef](DecisionOptionRef.Relic(circlet)))

  test("a facedown Circlet protects nothing"):
    val (_, relic) = raidBoard()
    assert(raidTargets(Some(Orientation.FaceDown))
      .contains(DecisionOptionRef.Relic(relic)))

  test("the holder's own Raid is not restricted by their Circlet"):
    // The defender holds no Circlet; the attacker does. The defender's things
    // stay targetable: the Circlet protects only its holder.
    val (_, relic) = raidBoard()
    assert(raidTargets(Some(Orientation.FaceUp), attacker = true)
      .contains(DecisionOptionRef.Relic(relic)))

  // ---- Challenge ----

  /** p1 challenges at Ancient City, where p2 stands holding People's Favor
    * and, on `circletSide`, the Circlet. */
  private def challenge(circletSide: Option[Orientation])
      : (PlayerId, OathTransition) =
    val table = Table.start
      .favor(p1, 6).secrets(p1, faceUp = 6, faceDown = 4)
      .peoplesFavor(Some(p2), favor = 2).pawn(p2, at = Table.homeOf(p1))
    val held = circletSide.fold(table)(side => table.relic(p2, circlet,
      facedown = side == Orientation.FaceDown))
    (p2, start(held.ready, ActionRef.Challenge, p1).toOption.get)

  private def challengeBanners(circletSide: Option[Orientation])
      : Vector[DecisionOptionRef] =
    optionsAt(challenge(circletSide)._2, ActionRef.Challenge)

  test("a Challenge may name any banner: the Circlet protects only a " +
      "Campaign's targets"):
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    assertEquals(challengeBanners(Some(Orientation.FaceUp)).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val other = RelicId("R10")

  /** The actor plays Conspiracy at a site the enemy shares; the enemy holds the
    * Circlet, one other relic and the People's Favor banner. Returns the
    * target options and the walk's events.
    */
  private def conspiracyTargets(circletSide: Orientation)
      : (ReadyGame, PlayerId, Vector[DecisionOptionRef], Vector[OathEvent]) =
    val base = PowerFixture.base
    val actor = PowerFixture.actor
    val enemy = base.game.current.players.map(_.player).find(_ != actor).get
    val site = PowerFixture.player(base).pawnSite
    val staged = CardStaging.without(CardStaging.without(base, conspiracy), other)
      .updateCurrent(c => c.copy(
        players = c.players.map(p => if p.player == enemy then
          p.copy(pawnSite = site) else p),
        banners = c.banners.copy(
          peoplesFavor = c.banners.peoplesFavor.copy(holder = Some(enemy)),
          darkestSecret = c.banners.darkestSecret.copy(holder = None)),
        temporaryHands = c.temporaryHands.updated(actor, Vector(conspiracy))))
    val ready = holds(holds(staged, enemy, other), enemy, circlet, circletSide)
    val hook = CardPlayedFaceup(conspiracy, RuleSourceRef.Adviser(actor, conspiracy))
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    val parked = ProcedureWalker.advance(ready, hook, None, powers).toOption.get
    val (options, events) = parked match
      case WalkerOutcome.Parked(pending, events) =>
        (ProcedureWalker.parkedDecide(ready, hook, pending, powers).get.query
          .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref), events)
      case _ => (Vector.empty, Vector.empty)
    (ready, enemy, options, events)

  test("Conspiracy may take any of the holder's relics and banners"):
    val (ready, enemy, options, _) = conspiracyTargets(Orientation.FaceUp)
    assertEquals(PowerFixture.player(ready, enemy).relics.map(_.id),
      Vector(other, circlet))
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0), DecisionOptionRef.RelicSlot(enemy, 1),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))

  test("a facedown Circlet leaves every target open"):
    val (_, enemy, options, _) = conspiracyTargets(Orientation.FaceDown)
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0), DecisionOptionRef.RelicSlot(enemy, 1),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))

  test("the Circlet's rule ignores an option that is not a banner or a relic"):
    val power = CircletOfCommand.forCatalog(catalog).get
    val ready = holds(PowerFixture.base, PowerFixture.actor, circlet)
    val ctx = PowerCtx(ready, PowerFixture.actor, power.source,
      PowerWindow.CampaignTargetSelection, Vector.empty,
      Decide("x", PowerFixture.actor, DecisionQuery.ChooseOne(Vector.empty)))
    val restriction = power.contributions(PowerWindow.CampaignTargetSelection)
      .head.asInstanceOf[oathdigital.gameplay.powerresolver.OptionRestriction]
    assertEquals(restriction.fn(ctx, DecisionOptionRef.Site(SiteId("s"))), None)

  // ---- Lines ----

  private val power = CircletOfCommand.forCatalog(catalog).get
  private def hidden(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)
  private def shielded(holder: PlayerId) = NoteText.Said("shielded",
    s"${holder.value}'s banners and relics cannot be targeted.", covers = false)

  test("the Raid targets the Circlet hides name their holder, once each"):
    val (b, _) = raidBoard()
    val said = hidden(raidKind(Some(Orientation.FaceUp)).events)
    assertEquals(said.size, 3)
    assertEquals(said.distinct, Vector(shielded(b.other)))
    assertEquals(hidden(raidKind(Some(Orientation.FaceDown)).events),
      Vector.empty)

  test("a Challenge writes no Circlet line"):
    assertEquals(hidden(challenge(Some(Orientation.FaceUp))._2.events),
      Vector.empty)

  test("a Conspiracy writes no Circlet line"):
    assertEquals(hidden(conspiracyTargets(Orientation.FaceUp)._4), Vector.empty)
