package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture.{RaidBoard, on, raidBoard}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayed
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PowerRules, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as, present}

class LostTongueSuite extends munit.FunSuite:
  import TargetingFixture._

  private val tongue = CatalogNames.denizen("Lost Tongue")
  /** A plain Nomad card: holding it faceup, a player rules a nomad card. */
  private val nomadCard = CatalogNames.denizen("Wild Mounts")
  private val crackedHorn = CatalogNames.relic("Cracked Horn")
  private val power = LostTongue.forCatalog(catalog)
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))
  private val parked = PowerRules.parkedUnderWalkerPowers

  private def notes(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  // ---- Raid ----

  /** The Raid's defender, their faceup relic and the kind answer. */
  private final case class RaidKind(defender: PlayerId, relic: RelicId,
      kind: OathTransition)

  /** The Raid board, the defender holding Lost Tongue on `side` and the
    * attacker a faceup nomad adviser when `nomad`, the Raid chosen. */
  private def raidKind(side: Orientation, nomad: Boolean): RaidKind =
    val RaidBoard(b, relic) = raidBoard()
    val held = on(b)(table =>
      val tongued = table.adviser(b.rival, tongue,
        facedown = side == Orientation.FaceDown)
      if nomad then tongued.adviser(b.actor, nomadCard) else tongued)
    val started = accepted(start(held.ready, ActionRef.Campaign, b.actor))
    RaidKind(b.rival, relic, accepted(rules.resolveWalker(started.state,
      b.actor, CampaignIds.kind, raid)))

  private def allTargets(relic: RelicId): Set[DecisionOptionRef] = Set(
    DecisionOptionRef.Relic(relic),
    DecisionOptionRef.Banner(Banner.PeoplesFavor),
    DecisionOptionRef.Banner(Banner.DarkestSecret))

  test("a Raid may not target the holder's relics or banners when the " +
      "attacker rules no nomad card"):
    val raided = raidKind(Orientation.FaceUp, nomad = false)
    // Every optional target is hidden, so the targets are not asked.
    assertEquals(parked.parkedDecision(raided.kind.state).map(_.decision),
      Some(CampaignIds.force))
    // The relic, the People's Favor and the Darkest Secret are hidden, each
    // with its reason on hover, and no line is written.
    assertEquals(notes(raided.kind.events), Vector.empty)

  test("an attacker who rules a nomad card may target them all"):
    val raided = raidKind(Orientation.FaceUp, nomad = true)
    assertEquals(optionsAt(raided.kind, ActionRef.Campaign).toSet,
      allTargets(raided.relic))
    assertEquals(notes(raided.kind.events), Vector.empty)

  test("a facedown Lost Tongue protects nothing"):
    val raided = raidKind(Orientation.FaceDown, nomad = false)
    assertEquals(optionsAt(raided.kind, ActionRef.Campaign).toSet,
      allTargets(raided.relic))

  // ---- Take ----

  test("its restriction refuses a Take of the holder's relic or banner by a " +
      "player who rules no nomad card, and nothing else"):
    val board = Table.start.adviser(p2, tongue).relic(p2, crackedHorn)
      .peoplesFavor(Some(p2), favor = 2)
    val ready = board.ready
    val withNomad = board.adviser(p1, nomadCard).ready
    val facedown = Table.start.adviser(p2, tongue, facedown = true)
      .relic(p2, crackedHorn).peoplesFavor(Some(p2), favor = 2).ready
    def refused(state: ReadyGame, operation: CoreOperation): Boolean =
      power.operationRestrictions.exists(_.reason(state, operation).nonEmpty)
    val mine = Location.PlayArea(p1)
    val theirs = Location.PlayArea(p2)
    val takeRelic = Take(Piece.Card(crackedHorn), p1, theirs, mine)
    val takeBanner = Take(Piece.Banner(Banner.PeoplesFavor), p1, theirs, mine)
    assert(refused(ready, takeRelic))
    assert(refused(ready, takeBanner))
    assert(!refused(withNomad, takeRelic))
    assert(!refused(withNomad, takeBanner))
    assert(!refused(facedown, takeRelic))
    assert(!refused(ready, Give(Piece.Card(crackedHorn), p2, theirs, mine)),
      "a Give is not a Take")
    assert(!refused(ready, Take(Piece.Favor(1), p1,
      Location.OnBanner(Banner.PeoplesFavor), mine)),
      "a banner's contents are not the banner")

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val hook = CardPlayedFaceup(conspiracy,
    RuleSourceRef.Adviser(p1, conspiracy))
  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** p1 plays Conspiracy at Ancient City, where p2 stands holding Lost Tongue
    * faceup, the People's Favor with 2 favor, and Cracked Horn when
    * `withRelic`. p1 holds a faceup nomad adviser when `nomad`. */
  private def conspiracyAt(withRelic: Boolean = true, nomad: Boolean = false)
      : ReadyGame =
    val staged = Table.start.pawn(p2, at = "Ancient City").hand(p1, conspiracy)
      .peoplesFavor(Some(p2), favor = 2).adviser(p2, tongue)
    val holding = if withRelic then staged.relic(p2, crackedHorn) else staged
    (if nomad then holding.adviser(p1, nomadCard) else holding).ready

  /** The target options and the pending tree, both empty when nothing was
    * asked, and the walk's events. */
  private final case class ConspiracyTargets(
      options: Vector[DecisionOptionRef], pending: Option[PendingTree],
      events: Vector[OathEvent])

  private def targets(ready: ReadyGame): ConspiracyTargets =
    accepted(ProcedureWalker.advance(ready, hook, None, powers)) match
      case WalkerOutcome.Parked(pending, events) =>
        val decide = present(ProcedureWalker.parkedDecide(ready, hook, pending,
          powers), "the target decision")
        ConspiracyTargets(
          as[DecisionQuery.ChooseOne](decide.query).options.map(_.ref),
          Some(pending), events)
      case WalkerOutcome.Finished(_, events) =>
        ConspiracyTargets(Vector.empty, None, events)

  /** Answers p1's target decision with `ref` and returns the state after. */
  private def take(ready: ReadyGame, ref: DecisionOptionRef): ReadyGame =
    val pending = present(targets(ready).pending, "a target decision")
    Outcomes.finished(ProcedureWalker.resolve(ready, hook, pending,
      Answered(ConspiracyWhenPlayed.decisionId, ChooseOneAnswer(ref), p1),
      powers)).treeless

  test("a Conspiracy is not offered the holder's banner, and writes no line"):
    val asked = targets(conspiracyAt())
    assertEquals(asked.options, Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(p2, 0)))
    assertEquals(notes(asked.events), Vector.empty)

  test("a Conspiracy may still take the holder's relic, which it gives"):
    val after = take(conspiracyAt(), DecisionOptionRef.RelicSlot(p2, 0))
    assert(Look(after).player(p1).relics.exists(_.id == crackedHorn))

  test("an actor who rules a nomad card takes the banner, its favor " +
      "returning to the banks"):
    val ready = conspiracyAt(nomad = true)
    val banner = DecisionOptionRef.Banner(Banner.PeoplesFavor)
    assert(targets(ready).options.contains(banner))
    val after = take(ready, banner)
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(p1))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    // The banner's 2 favor go back to the banks.
    assertEquals(after.banks.favor.values.sum, ready.banks.favor.values.sum + 2)

  test("a Conspiracy left with only the holder's banner asks nothing"):
    val asked = targets(conspiracyAt(withRelic = false))
    assertEquals(asked.options, Vector.empty)
    assertEquals(asked.pending, None)

  // ---- Challenge ----

  /** p1 challenges at Ancient City, where p2 stands holding Lost Tongue and
    * People's Favor; p1 holds a faceup nomad adviser when `nomad`. */
  private def challengeBanners(nomad: Boolean): Vector[DecisionOptionRef] =
    val table = challengeTable.adviser(p2, tongue)
    val ready = (if nomad then table.adviser(p1, nomadCard) else table).ready
    optionsAt(accepted(start(ready, ActionRef.Challenge, p1)),
      ActionRef.Challenge)

  test("a Challenge is not offered the holder's banner: its custody Take " +
      "would be refused"):
    assertEquals(challengeBanners(nomad = false),
      Vector[DecisionOptionRef](DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(nomad = true).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
