package oathdigital.gameplay.powers.burn

import oathdigital.gameplay.CampaignFixture.{on, raidBoard, rules,
  withAdviser, withAdviserFor}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.action.DemonTail
import oathdigital.gameplay.powers.campaign.PlanDriver.{awaits, commit,
  winningDice}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class VowOfSilenceSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Silence")
  private val tail = CatalogNames.relic("Demon Tail")
  private val bookBurning = CatalogNames.denizen("Book Burning")
  private val game = ActPowerHarness.rulesWithPrintedRestrictions()

  /** The lines Vow of Silence journaled in `events`. */
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfSilence.id, VowOfSilence.noteKeys, events)

  /** p1's Act holding Demon Tail (burn 3 secrets: gain 2 Supply) with 3
    * faceup secrets and 2 Supply, and `holder` holding Vow of Silence
    * faceup. */
  private def demonTailWithVowHeldBy(holder: PlayerId): ReadyGame =
    Table.start.turn(p1, Phase.Act).relic(p1, tail).supply(p1, 2)
      .secrets(p1, 3).adviser(holder, vow).ready

  /** p1 at Ancient City, holding Vow of Silence, can challenge p2 there for
    * the People's Favor (2 favor) or the Darkest Secret (1 secret). */
  private val challengeForEither: Table = TargetingFixture.challengeTable
    .darkestSecret(Some(p2), secrets = 1).adviser(p1, vow)

  test("an enemy's burnt cost goes to the holder, faceup, with its line"):
    val ready0 = demonTailWithVowHeldBy(p2)
    val done = accepted(ActPowerHarness.use(game, ready0, DemonTail.id,
      DecisionOptionRef.Relic(tail)))
    val end = Outcomes.ready(done.state)
    assertEquals(Look(end).faceUpSecrets(p1), 0)
    assertEquals(Look(end).faceUpSecrets(p2),
      Look(ready0).faceUpSecrets(p2) + 3)
    assertEquals(said(done.events), Vector(NoteText.Said("taken-by",
      s"${p2.value} took ${p1.value}'s 3 burned secrets.", covers = false)))
    assertEquals(ActPowerHarness.replayed(game, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("the holder's own burnt cost goes to the bank"):
    val ready0 = demonTailWithVowHeldBy(p1)
    val done = accepted(ActPowerHarness.use(game, ready0, DemonTail.id,
      DecisionOptionRef.Relic(tail)))
    assertEquals(Look(Outcomes.ready(done.state)).faceUpSecrets(p1), 0)
    assertEquals(said(done.events), Vector.empty)

  test("a defender holding it takes the Darkest Secret's secrets the " +
      "attacker burns as they take it"):
    val raid = raidBoard()
    val b = withAdviserFor(raid.board, raid.board.rival, vow,
      Orientation.FaceUp)
    val before = Look(b.ready).faceUpSecrets(b.rival)
    val run = commit(rules(winningDice), b, 4, targets = Vector(
      DecisionOptionRef.Banner(Banner.DarkestSecret)), raid = true)
      .finishPlanWindows
    assertEquals(run.winner, Some(true))
    assert(awaits(run, b.actor, CampaignIds.relocation))
    val after = Outcomes.ready(run.state).game.current
    assertEquals(BannerRules.holder(after, Banner.DarkestSecret),
      Some(b.actor))
    assertEquals(after.banners.darkestSecret.secrets, 0)
    assertEquals(Look(run.state).faceUpSecrets(b.rival), before + 2)
    assertEquals(said(run.events), Vector(NoteText.Said("taken-by",
      s"${b.rival.value} took ${b.actor.value}'s 2 burned secrets.",
      covers = false)))

  test("a bystander holding it takes the secrets Book Burning burns, and " +
      "the facedown ones stay facedown"):
    val raid = raidBoard()
    val staged = on(raid.board)(_.secrets(raid.board.rival, faceUp = 1,
      faceDown = 2))
    val b = withAdviserFor(withAdviser(staged, bookBurning,
      Orientation.FaceUp), staged.bystander, vow, Orientation.FaceUp)
    val faceDownBefore = Look(b.ready).faceDownSecrets(b.bystander)
    val run = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(raid.relic)), raid = true)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(bookBurning))
      .finishPlanWindows
    assertEquals(run.winner, Some(true))
    val done = run.pick(b.actor, CampaignIds.relocation,
      run.offered(b.actor).head).finishPlanWindows
    // Book Burning keeps one secret: the 2 facedown ones burn.
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 0)
    assertEquals(Look(done.state).faceDownSecrets(b.bystander),
      faceDownBefore + 2)
    assertEquals(said(done.events), Vector(NoteText.Said("taken-by",
      s"${b.bystander.value} took ${b.actor.value}'s 2 burned secrets.",
      covers = false)))

  test("its holder sees the Darkest Secret unavailable, with the reason, " +
      "and may still challenge for the People's Favor"):
    val started = accepted(TargetingFixture.start(challengeForEither.ready,
      ActionRef.Challenge, p1))
    val banners = TargetingFixture.optionsIncludingUnavailableAt(started,
      ActionRef.Challenge)
    assertEquals(banners.find(_.ref ==
      DecisionOptionRef.Banner(Banner.PeoplesFavor)).map(_.available),
      Some(true))
    assertEquals(banners.collectFirst {
      case DecisionOption.Unavailable(option, reason)
          if option.ref == DecisionOptionRef.Banner(Banner.DarkestSecret) =>
        reason
    }, Some(OptionReason.Noted(VowOfSilence.id, VowOfSilence.forbidden(
      PowerSourceRef.Card(vow), NoteArg.Banner(Banner.DarkestSecret)))))

  test("a holder who could challenge only for the Darkest Secret cannot " +
      "start a Challenge, and the menu says why"):
    // p1 holds the People's Favor already, so it is not theirs to challenge.
    val ready0 = Table.start.pawn(p1, at = "Ancient City").favor(p1, 6)
      .secrets(p1, faceUp = 6, faceDown = 4)
      .peoplesFavor(Some(p1), favor = 2)
      .darkestSecret(Some(p2), secrets = 1)
      .pawn(p2, at = "Ancient City").adviser(p1, vow).ready
    MenuPlay.menuOptionFor(catalog, ready0, ActionRef.Challenge) match
      case DecisionOption.Unavailable(_, reason) => assertEquals(reason,
        OptionReason.Written("Vow of Silence prevents you from " +
          "challenging the Darkest Secret"))
      case other => fail(s"the Challenge is offered: $other")
