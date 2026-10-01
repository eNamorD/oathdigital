package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture.raidBoard
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.{CardStaging, NoteText, PowerFixture,
  SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayed
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

class LostTongueSuite extends munit.FunSuite:
  import TargetingFixture._

  private val tongue = CatalogNames.denizen("Lost Tongue")
  /** A plain Nomad card: holding it faceup, a player rules a nomad card. */
  private val nomadCard = SearchFixture.denizensOf(Suit.Nomad).head
  private val power = LostTongue.forCatalog(catalog).get
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private def notes(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)
  private def shielded(holder: PlayerId) = NoteText.Said("shielded",
    s"${holder.value}'s banners and relics cannot be targeted.", covers = false)

  private def adviser(state: ReadyGame, player: PlayerId, card: DenizenId,
      side: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(state, player, card, side)

  // ---- Raid ----

  /** The Raid board, the defender holding Lost Tongue on `side` and the
    * attacker a faceup nomad adviser when `nomad`, the Raid chosen. Returns
    * the defender, their faceup relic and the transition of the kind answer.
    */
  private def raidKind(side: Orientation, nomad: Boolean)
      : (PlayerId, RelicId, OathTransition) =
    val (b, relic) = raidBoard()
    val held = adviser(b.ready, b.other, tongue, side)
    val ready = if nomad then adviser(held, b.actor, nomadCard) else held
    val started = start(ready, ActionRef.Campaign, b.actor).toOption.get
    (b.other, relic, rules.resolveWalker(started.state, b.actor,
      CampaignIds.kind, raid).toOption.get)

  private def allTargets(relic: RelicId): Set[DecisionOptionRef] = Set(
    DecisionOptionRef.Relic(relic), DecisionOptionRef.Banner(Banner.PeoplesFavor),
    DecisionOptionRef.Banner(Banner.DarkestSecret))

  test("a Raid may not target the holder's relics or banners when the " +
      "attacker rules no nomad card"):
    val (defender, _, kind) = raidKind(Orientation.FaceUp, nomad = false)
    // Every optional target is hidden, so the targets are not asked.
    assertEquals(parked.parkedDecision(kind.state).map(_.decision),
      Some(CampaignIds.force))
    val said = notes(kind.events)
    assertEquals(said.size, 3)
    assertEquals(said.distinct, Vector(shielded(defender)))

  test("an attacker who rules a nomad card may target them all"):
    val (_, relic, kind) = raidKind(Orientation.FaceUp, nomad = true)
    assertEquals(optionsAt(kind, ActionRef.Campaign).toSet, allTargets(relic))
    assertEquals(notes(kind.events), Vector.empty)

  test("a facedown Lost Tongue protects nothing"):
    val (_, relic, kind) = raidKind(Orientation.FaceDown, nomad = false)
    assertEquals(optionsAt(kind, ActionRef.Campaign).toSet, allTargets(relic))

  // ---- Take ----

  test("its restriction refuses a Take of the holder's relic or banner by a " +
      "player who rules no nomad card, and nothing else"):
    val relic = RelicId("R10")
    val board = Table.start.adviser(p2, tongue).relic(p2, relic)
      .peoplesFavor(Some(p2), favor = 2)
    val ready = board.ready
    val withNomad = board.adviser(p1, nomadCard).ready
    val facedown = Table.start.adviser(p2, tongue, facedown = true)
      .relic(p2, relic).peoplesFavor(Some(p2), favor = 2).ready
    def refused(state: ReadyGame, operation: CoreOperation): Boolean =
      power.operationRestrictions.exists(_.reason(state, operation).nonEmpty)
    val mine = Location.PlayArea(p1)
    val theirs = Location.PlayArea(p2)
    val takeRelic = Take(Piece.Card(relic), p1, theirs, mine)
    val takeBanner = Take(Piece.Banner(Banner.PeoplesFavor), p1, theirs, mine)
    assert(refused(ready, takeRelic))
    assert(refused(ready, takeBanner))
    assert(!refused(withNomad, takeRelic))
    assert(!refused(withNomad, takeBanner))
    assert(!refused(facedown, takeRelic))
    assert(!refused(ready, Give(Piece.Card(relic), p2, theirs, mine)),
      "a Give is not a Take")
    assert(!refused(ready, Take(Piece.Favor(1), p1,
      Location.OnBanner(Banner.PeoplesFavor), mine)),
      "a banner's contents are not the banner")

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val relic = RelicId("R10")
  private val hook = CardPlayedFaceup(conspiracy,
    RuleSourceRef.Adviser(PowerFixture.actor, conspiracy))
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)

  /** The actor plays Conspiracy at a site the enemy shares. The enemy holds
    * Lost Tongue faceup, the People's Favor with 2 favor, and relic R10 when
    * `withRelic`. The actor holds a faceup nomad adviser when `nomad`.
    * Returns the state, the actor and the enemy.
    */
  private def conspiracyAt(withRelic: Boolean = true, nomad: Boolean = false)
      : (ReadyGame, PlayerId, PlayerId) =
    val base = PowerFixture.base
    val actor = PowerFixture.actor
    val enemy = TargetsFixture.others(base).head
    val site = PowerFixture.player(base).pawnSite
    val staged = CardStaging.without(CardStaging.without(base, conspiracy), relic)
      .updateCurrent(c => c.copy(
        players = c.players.map(p => if p.player == enemy then
          p.copy(pawnSite = site) else p),
        banners = c.banners.copy(
          peoplesFavor = c.banners.peoplesFavor.copy(holder = Some(enemy),
            favor = 2),
          darkestSecret = c.banners.darkestSecret.copy(holder = None)),
        temporaryHands = c.temporaryHands.updated(actor, Vector(conspiracy))))
    val held = adviser(if withRelic then holds(staged, enemy, relic) else staged,
      enemy, tongue)
    (if nomad then adviser(held, actor, nomadCard) else held, actor, enemy)

  /** The target options and the pending tree, both empty when nothing was
    * asked, and the walk's events. */
  private def targets(ready: ReadyGame)
      : (Vector[DecisionOptionRef], Option[PendingTree], Vector[OathEvent]) =
    ProcedureWalker.advance(ready, hook, None, powers).toOption.get match
      case WalkerOutcome.Parked(pending, events) =>
        (ProcedureWalker.parkedDecide(ready, hook, pending, powers).get.query
          .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref),
          Some(pending), events)
      case WalkerOutcome.Finished(_, events) => (Vector.empty, None, events)

  /** Answers the target decision with `ref` and returns the state after. */
  private def take(ready: ReadyGame, actor: PlayerId, ref: DecisionOptionRef)
      : ReadyGame = ProcedureWalker.resolve(ready, hook, targets(ready)._2.get,
    Answered(ConspiracyWhenPlayed.decisionId, ChooseOneAnswer(ref), actor),
    powers).toOption.get.asInstanceOf[WalkerOutcome.Finished].treeless

  test("a Conspiracy is not offered the holder's banner, and writes no line"):
    val (ready, _, enemy) = conspiracyAt()
    val (options, _, events) = targets(ready)
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0)))
    assertEquals(notes(events), Vector.empty)

  test("a Conspiracy may still take the holder's relic, which it gives"):
    val (ready, actor, enemy) = conspiracyAt()
    val after = take(ready, actor, DecisionOptionRef.RelicSlot(enemy, 0))
    assert(PowerFixture.player(after, actor).relics.exists(_.id == relic))

  test("an actor who rules a nomad card takes the banner, its favor " +
      "returning to the banks"):
    val (ready, actor, _) = conspiracyAt(nomad = true)
    assert(targets(ready)._1.contains(DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    val after = take(ready, actor, DecisionOptionRef.Banner(Banner.PeoplesFavor))
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(after.banks.favor.values.sum, ready.banks.favor.values.sum + 2)

  test("a Conspiracy left with only the holder's banner asks nothing"):
    val (ready, _, _) = conspiracyAt(withRelic = false)
    val (options, pending, _) = targets(ready)
    assertEquals(options, Vector.empty)
    assertEquals(pending, None)

  // ---- Challenge ----

  /** p1 challenges at Ancient City, where p2 stands holding Lost Tongue and
    * People's Favor; p1 holds a faceup nomad adviser when `nomad`. */
  private def challengeBanners(nomad: Boolean): Vector[DecisionOptionRef] =
    val table = Table.start
      .favor(p1, 6).secrets(p1, faceUp = 6, faceDown = 4)
      .peoplesFavor(Some(p2), favor = 2).pawn(p2, at = Table.homeOf(p1))
      .adviser(p2, tongue)
    val ready = if nomad then table.adviser(p1, nomadCard).ready else table.ready
    optionsAt(start(ready, ActionRef.Challenge, p1).toOption.get,
      ActionRef.Challenge)

  test("a Challenge is not offered the holder's banner: its custody Take " +
      "would be refused"):
    assertEquals(challengeBanners(nomad = false),
      Vector[DecisionOptionRef](DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(nomad = true).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
