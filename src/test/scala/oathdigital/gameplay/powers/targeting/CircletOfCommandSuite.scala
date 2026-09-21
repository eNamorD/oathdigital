package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.{CampaignFixture, ChallengeFixture}
import oathdigital.gameplay.CampaignFixture.raidBoard
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class CircletOfCommandSuite extends munit.FunSuite {
  import TargetingFixture._

  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))

  test("the Circlet is a registered persistent rule, so it is automatic") {
    val power = CircletOfCommand.forCatalog(catalog).get
    assertEquals(power.cardId, circlet)
    assertEquals(power.resolution, PowerResolution.Automatic)
  }

  // ---- Raid ----

  /** The Raid board with the Circlet faceup on the defender, beside the relic and
    * the banners the board already gives them. Returns the Raid's target options.
    */
  private def raidTargets(circletSide: Option[Orientation],
      attacker: Boolean = false): Vector[DecisionOptionRef] = {
    val (b, _) = raidBoard()
    val held = circletSide.fold(b.ready)(side =>
      holds(b.ready, if (attacker) b.actor else b.other, circlet, side))
    val started = start(held, ActionRef.Campaign, b.actor).toOption.get
    val kind = rules.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get
    optionsAt(kind, ActionRef.Campaign)
  }

  test("a Raid may not target the holder's other relics or banners, but may " +
      "target the Circlet") {
    val (b, relic) = raidBoard()
    assertEquals(raidTargets(None).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Relic(relic), DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(raidTargets(Some(Orientation.FaceUp)),
      Vector[DecisionOptionRef](DecisionOptionRef.Relic(circlet)))
  }

  test("a facedown Circlet protects nothing") {
    val (_, relic) = raidBoard()
    assert(raidTargets(Some(Orientation.FaceDown))
      .contains(DecisionOptionRef.Relic(relic)))
  }

  test("the holder's own Raid is not restricted by their Circlet") {
    // The defender holds no Circlet; the attacker does. The defender's things
    // stay targetable: the Circlet protects only its holder.
    val (_, relic) = raidBoard()
    assert(raidTargets(Some(Orientation.FaceUp), attacker = true)
      .contains(DecisionOptionRef.Relic(relic)))
  }

  // ---- Challenge ----

  private def challengeBanners(circletSide: Option[Orientation])
      : Vector[DecisionOptionRef] = {
    val (base, _) = ChallengeFixture.ready(resources = 2)
    val actor = ChallengeFixture.active(base)
    val withHolder = ChallengeFixture.enemyHolds(base, Banner.PeoplesFavor, 2)
    val enemy = ChallengeFixture.enemy(withHolder).player
    val held = circletSide.fold(withHolder)(holds(withHolder, enemy, circlet, _))
    optionsAt(start(held, ActionRef.Challenge, actor).toOption.get,
      ActionRef.Challenge)
  }

  test("a Challenge may not name a banner its holder's Circlet protects") {
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    assertEquals(challengeBanners(None).toSet, Set(banner(Banner.PeoplesFavor),
      banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(Some(Orientation.FaceUp)),
      Vector(banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(Some(Orientation.FaceDown)).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))
  }

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val other = RelicId("R10")

  /** The actor plays Conspiracy at a site the enemy shares; the enemy holds the
    * Circlet, one other relic and the People's Favor banner.
    */
  private def conspiracyTargets(circletSide: Orientation)
      : (ReadyGame, PlayerId, Vector[DecisionOptionRef]) = {
    val base = PowerFixture.base
    val actor = PowerFixture.actor
    val enemy = base.game.current.players.map(_.player).find(_ != actor).get
    val current = base.game.current
    val site = PowerFixture.player(base).pawnSite
    val staged = CardStaging.without(CardStaging.without(base, conspiracy), other)
      .updateCurrent(c => c.copy(
        players = c.players.map(p => if (p.player == enemy)
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
    val options = parked match {
      case WalkerOutcome.Parked(pending, _) =>
        ProcedureWalker.parkedDecide(ready, hook, pending, powers).get.query
          .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)
      case _ => Vector.empty
    }
    (ready, enemy, options)
  }

  test("Conspiracy may take the Circlet, but not the holder's other relic or banner") {
    val (_, enemy, options) = conspiracyTargets(Orientation.FaceUp)
    val slots = PowerFixture.player(conspiracyTargets(Orientation.FaceUp)._1, enemy)
      .relics.map(_.id)
    assertEquals(slots, Vector(other, circlet))
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 1)))
  }

  test("a facedown Circlet leaves every target open") {
    val (_, enemy, options) = conspiracyTargets(Orientation.FaceDown)
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0), DecisionOptionRef.RelicSlot(enemy, 1),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
  }

  test("the Circlet's rule ignores an option that is not a banner or a relic") {
    val power = CircletOfCommand.forCatalog(catalog).get
    val ready = holds(PowerFixture.base, PowerFixture.actor, circlet)
    val ctx = PowerCtx(ready, PowerFixture.actor, power.source,
      PowerWindow.CampaignTargetSelection, Vector.empty,
      Decide("x", PowerFixture.actor, DecisionQuery.ChooseOne(Vector.empty)))
    val restriction = power.contributions(PowerWindow.CampaignTargetSelection)
      .head.asInstanceOf[oathdigital.gameplay.powerresolver.OptionRestriction]
    assertEquals(restriction.fn(ctx, DecisionOptionRef.Site(SiteId("s"))), None)
  }
}
