package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.CampaignFixture.{on, raidBoard, rules}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver.{commit, winningDice}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class SecretTestamentSuite extends munit.FunSuite:
  private val testament = CatalogNames.relic("Secret Testament")
  private val game = ActPowerHarness.rulesWithPrintedRestrictions()

  /** The lines Secret Testament journaled in `events`. */
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(SecretTestament.id, SecretTestament.noteKeys, events)

  /** p1 at Ancient City (6 favor, 6 faceup and 4 facedown secrets) can
    * challenge p2 there, who holds the People's Favor with 2 favor, the
    * Darkest Secret with 3 secrets, and Secret Testament (facedown when
    * `facedown`). */
  private def challengingTestamentHolder(facedown: Boolean = false)
      : ReadyGame = TargetingFixture.challengeTable
    .darkestSecret(Some(p2), secrets = 3)
    .relic(p2, testament, facedown = facedown).ready

  /** p1's Challenge for `banner`, paying `amount`, with every command's
    * events. */
  private def challenged(board: ReadyGame, banner: Banner, amount: Int)
      : OathTransition =
    val started = accepted(game.startWalker(Ready(board), ActionRef.Challenge,
      p1))
    val chosen = accepted(game.resolveWalker(started.state, p1,
      "challenge.banner", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(banner))))
    val paid = accepted(game.resolveWalker(chosen.state, p1,
      "challenge.amount", DecisionAnswer.ChooseAmountAnswer(amount)))
    paid.copy(events = started.events ++ chosen.events ++ paid.events)

  test("an enemy who challenges for the People's Favor pays onto an empty " +
      "banner, and the holder takes its favor"):
    val board = challengingTestamentHolder()
    val done = challenged(board, Banner.PeoplesFavor, 3)
    val end = Outcomes.ready(done.state)
    val banner = end.game.current.banners.peoplesFavor
    assertEquals((banner.holder, banner.favor), (Some(p1), 3))
    assertEquals(Look(end).favor(p2), Look(board).favor(p2) + 2)
    assertEquals(end.banks.favor, board.banks.favor)
    assertEquals(said(done.events), Vector(NoteText.Said("took-favor",
      s"${p2.value} took 2 favor from the People's Favor.",
      covers = false)))
    assertEquals(ActPowerHarness.replayed(game, board, done.events), end)
    assertWireRoundTrips(done.events)

  test("for the Darkest Secret the holder takes every secret, and none " +
      "go to sites"):
    val board = challengingTestamentHolder()
    val done = challenged(board, Banner.DarkestSecret, 4)
    val end = Outcomes.ready(done.state)
    val banner = end.game.current.banners.darkestSecret
    assertEquals((banner.holder, banner.secrets), (Some(p1), 4))
    assertEquals(Look(end).faceUpSecrets(p2),
      Look(board).faceUpSecrets(p2) + 3)
    assertEquals(end.game.current.map.sites.view.mapValues(_.tokens).toMap,
      board.game.current.map.sites.view.mapValues(_.tokens).toMap)
    assertEquals(said(done.events), Vector(NoteText.Said("took-secrets",
      s"${p2.value} took 3 secrets from the Darkest Secret.",
      covers = false)))

  test("a facedown Secret Testament leaves the ribbon as it is"):
    val board = challengingTestamentHolder(facedown = true)
    val end = Outcomes.ready(challenged(board, Banner.PeoplesFavor, 3).state)
    assertEquals(end.banks.favor, BannerRules.addFavor(board.banks.favor,
      BannerRules.raidFavorReturn(board.banks.favor, 2)))
    assertEquals(Look(end).favor(p2), Look(board).favor(p2))

  test("a Raid that takes the banner from its holder is not changed"):
    val raid = raidBoard()
    def raided(holding: Boolean): ReadyGame =
      val b = if holding then on(raid.board)(_.relic(raid.board.rival,
        testament)) else raid.board
      val run = commit(rules(winningDice), b, 4, targets = Vector(
        DecisionOptionRef.Banner(Banner.PeoplesFavor)), raid = true)
        .finishPlanWindows
      assertEquals(run.winner, Some(true))
      val done = run.pick(b.actor, CampaignIds.relocation,
        run.offered(b.actor).head).finishPlanWindows
      assertEquals(said(done.events), Vector.empty)
      Outcomes.ready(done.state)
    val (plain, held) = (raided(holding = false), raided(holding = true))
    assertEquals(Look(held).favor(raid.board.rival),
      Look(plain).favor(raid.board.rival))
    assertEquals(held.banks.favor, plain.banks.favor)
