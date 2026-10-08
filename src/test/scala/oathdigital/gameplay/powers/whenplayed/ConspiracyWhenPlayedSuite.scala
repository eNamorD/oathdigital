package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.PlacementFixture.cardPlayDecisionId
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  PlacementPicks, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as, present, rejected}

class ConspiracyWhenPlayedSuite extends munit.FunSuite:
  import WhenPlayedHarness.replayed

  private val powers = WalkerPowers(Vector(ConspiracyWhenPlayed))
  private val conspiracy = VisionRules.Conspiracy
  private val placeId = cardPlayDecisionId(conspiracy, "place")
  private val crackedHorn = CatalogNames.relic("Cracked Horn")
  private val banditCrown = CatalogNames.relic("Bandit Crown")

  /** The game, and where p1 plays Conspiracy from. */
  private final case class Staged(ready: ReadyGame,
      origin: CardPlayProcedure.Origin)

  /** p1 plays Conspiracy from a temporary hand. */
  private def fromHand(table: Table): Staged =
    Staged(table.hand(p1, conspiracy).ready,
      CardPlayProcedure.Origin.TemporaryHand)

  /** p1 plays Conspiracy from a facedown adviser. */
  private def fromFacedownAdviser(table: Table): Staged =
    Staged(table.adviser(p1, conspiracy, facedown = true).ready,
      CardPlayProcedure.Origin.FacedownAdviser)

  /** p2 stands on p1's site, Ancient City. */
  private val p2AtAncientCity = Table.start.pawn(p2, "Ancient City")

  private def treeFor(f: Staged): Operation = accepted(f.origin match {
    case CardPlayProcedure.Origin.TemporaryHand =>
      CardPlayProcedure.build(catalog, f.ready, p1, conspiracy, f.origin)
    case CardPlayProcedure.Origin.FacedownAdviser =>
      CardPlayProcedure.rebuildFacedown(catalog, f.ready, p1,
        Vector(DecisionOptionRef.Vision(conspiracy)))
  })

  private def answer(f: Staged, tree: Operation, at: PendingTree, id: String,
      ref: DecisionOptionRef): Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(f.ready, tree, at, Answered(id,
      DecisionAnswer.ChooseOneAnswer(ref), p1), powers)

  /** The play's tree, and the position parked on the placement decision. */
  private final case class AtPlacement(tree: Operation, at: PendingTree)

  /** Plays Conspiracy and parks on the placement decision. */
  private def atPlacement(f: Staged): AtPlacement =
    val tree = treeFor(f)
    val parked = Outcomes.parked(ProcedureWalker.advance(f.ready, tree, None,
      powers))
    AtPlacement(tree, parked.tree)

  /** The option of the parked placement decision that plays the card
    * `way`. */
  private def waying(f: Staged, placement: AtPlacement, way: PlayedAs)
      : DecisionOptionRef =
    PlacementPicks.pick(present(ProcedureWalker.parkedDecide(f.ready,
      placement.tree, placement.at, powers), "the placement decision"), way)

  /** Answers the placement decision by playing the card `way`. */
  private def placedAs(f: Staged, way: PlayedAs)
      : Either[OathViolation, WalkerOutcome] =
    val placement = atPlacement(f)
    answer(f, placement.tree, placement.at, placeId,
      waying(f, placement, way))

  /** The play's tree, and the position parked on the target decision. */
  private final case class AtTarget(tree: Operation, at: PendingTree)

  /** Plays Conspiracy faceup and parks on the target decision. */
  private def atTarget(f: Staged): AtTarget =
    val placement = atPlacement(f)
    val placed = answer(f, placement.tree, placement.at, placeId,
      waying(f, placement, PlayedAs.Faceup))
    AtTarget(placement.tree, Outcomes.parked(placed).tree)

  private final case class TargetDecision(decisionId: String, owner: PlayerId,
      options: Vector[DecisionOptionRef])

  private def targetDecision(f: Staged, target: AtTarget)
      : Option[TargetDecision] =
    ProcedureWalker.parkedDecide(f.ready, target.tree, target.at, powers)
      .map(decide => TargetDecision(decide.decisionId, decide.owner,
        as[DecisionQuery.ChooseOne](decide.query).options.map(_.ref)))

  /** Answers the target decision with `ref`, to the end of the play. */
  private def take(f: Staged, target: AtTarget, ref: DecisionOptionRef)
      : WalkerOutcome.Finished =
    Outcomes.finished(answer(f, target.tree, target.at,
      ConspiracyWhenPlayed.decisionId, ref))

  test("Conspiracy takes an opaque relic slot and leaves the game"):
    val f = fromHand(p2AtAncientCity.relic(p2, crackedHorn, facedown = true))
    val target = atTarget(f)
    assertEquals(targetDecision(f, target), Some(TargetDecision(
      ConspiracyWhenPlayed.decisionId, p1,
      Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(p2, 0)))))
    val done = take(f, target, DecisionOptionRef.RelicSlot(p2, 0))
    val after = done.treeless
    assert(Look(after).player(p1).relics.exists(_.id == crackedHorn))
    assert(!Look(after).player(p2).relics.exists(_.id == crackedHorn))
    assertEquals(after.game.current.temporaryHands(p1), Vector.empty)
    assert(!accepted(CardIndex.from(after.game)).ids.contains(conspiracy))
    assertEquals(replayed(f.ready, done.events), after)

  test("Conspiracy taking the Peoples Favor returns its favor in the " +
      "least-bank order and takes the banner"):
    // The banner's 2 favor go back one at a time, each to the bank holding
    // the least.
    val f = fromHand(p2AtAncientCity.peoplesFavor(Some(p2), 2)
      .bankFavor(Suit.Discord, 0).bankFavor(Suit.Arcane, 2)
      .bankFavor(Suit.Order, 3).bankFavor(Suit.Hearth, 4)
      .bankFavor(Suit.Beast, 5).bankFavor(Suit.Nomad, 6))
    val target = atTarget(f)
    val banner = DecisionOptionRef.Banner(Banner.PeoplesFavor)
    assertEquals(targetDecision(f, target), Some(TargetDecision(
      ConspiracyWhenPlayed.decisionId, p1, Vector[DecisionOptionRef](banner))))
    val done = take(f, target, banner)
    val after = done.treeless
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(p1))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    // The six banks, and the banner's 2 favor returned.
    assertEquals(after.banks.favor.values.sum, 0 + 2 + 3 + 4 + 5 + 6 + 2)
    // The favor returns as the banner Take's children.
    val returned = PhasePowerHarness.ops(done.events)
      .flatMap(Operation.flatten).collect:
        case Move(Piece.Favor(1), _,
            PositionedLocation(Location.FavorBank(suit), _), _) => suit
    // Discord holds the least before each favor: 0, then 1.
    assertEquals(returned, Vector(Suit.Discord, Suit.Discord))
    assertEquals(after.game.current.temporaryHands(p1), Vector.empty)
    assertEquals(replayed(f.ready, done.events), after)

  test("Conspiracy taking the Darkest Secret burns every secret and takes " +
      "the banner"):
    val f = fromHand(p2AtAncientCity.darkestSecret(Some(p2), 3))
    def siteSecrets(ready: ReadyGame): Int =
      ready.game.current.map.sites.valuesIterator.map { site =>
        site.tokens.secrets + site.denizens.collect {
          case card: DenizenState => card.tokens.secrets
        }.sum
      }.sum
    val done = take(f, atTarget(f),
      DecisionOptionRef.Banner(Banner.DarkestSecret))
    val after = done.treeless
    assertEquals(after.game.current.banners.darkestSecret.secrets, 0)
    assertEquals(after.game.current.banners.darkestSecret.holder, Some(p1))
    // The burn returns the secrets to the untracked shared bank; none lands on
    // a site.
    assertEquals(siteSecrets(after), siteSecrets(f.ready))
    val ops = PhasePowerHarness.ops(done.events)
    assert(ops.contains(Take(Piece.Banner(Banner.DarkestSecret),
      p1, Location.PlayArea(p2), Location.PlayArea(p1),
      leaving = Vector(Burn.secrets(3,
        PositionedLocation(Location.OnBanner(Banner.DarkestSecret)))))),
      ops.toString)
    assertEquals(replayed(f.ready, done.events), after)

  test("with no legal target Conspiracy asks nothing and only leaves the game"):
    // p2 stands at Broken Peaks, away from p1, and no banner has a holder.
    val f = fromHand(Table.start)
    val done = Outcomes.finished(placedAs(f, PlayedAs.Faceup))
    val after = done.treeless
    assertEquals(after.game.current.temporaryHands(p1), Vector.empty)
    assert(!accepted(CardIndex.from(after.game)).ids.contains(conspiracy))
    assertEquals(PhasePowerHarness.ops(done.events), Vector[CoreOperation](Move(
      Piece.Card(conspiracy), PositionedLocation(Location.Hand(p1)),
      PositionedLocation(Location.SharedBank))))

  test("a target the current state does not offer is rejected at the decision"):
    val f = fromHand(p2AtAncientCity.relic(p2, crackedHorn, facedown = true)
      .relic(p2, banditCrown, facedown = true))
    val target = atTarget(f)
    assertEquals(targetDecision(f, target).map(_.options),
      Some(Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(p2, 0),
        DecisionOptionRef.RelicSlot(p2, 1))))
    Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(p2, 2),
      DecisionOptionRef.RelicSlot(p1, 0),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)).foreach { ref =>
      assertEquals(rejected(answer(f, target.tree, target.at,
        ConspiracyWhenPlayed.decisionId, ref)), OathViolation
        .InvalidEventOrder(s"decision ${ConspiracyWhenPlayed.decisionId} " +
          "does not offer the selected option"), ref.toString)
    }

  test("discarding a Conspiracy does not play it"):
    val f = fromHand(p2AtAncientCity.relic(p2, crackedHorn, facedown = true))
    val done = Outcomes.finished(placedAs(f, PlayedAs.Discarded))
    assert(Look(done.treeless).player(p2).relics.exists(_.id == crackedHorn))
    assert(accepted(CardIndex.from(done.treeless.game)).ids
      .contains(conspiracy))

  test("a Conspiracy played from a facedown adviser takes its target and " +
      "leaves the revealed Vision alone"):
    val f = fromFacedownAdviser(p2AtAncientCity.relic(p2, crackedHorn,
      facedown = true))
    val revealedBefore = Look(f.ready).player(p1).revealedVision
    val target = atTarget(f)
    assertEquals(targetDecision(f, target).map(_.options),
      Some(Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(p2, 0))))
    val done = take(f, target, DecisionOptionRef.RelicSlot(p2, 0))
    val after = done.treeless
    assert(Look(after).player(p1).relics.exists(_.id == crackedHorn))
    assert(!Look(after).player(p1).advisers.exists(_.id == conspiracy))
    assertEquals(Look(after).player(p1).revealedVision, revealedBefore)
    assert(!accepted(CardIndex.from(after.game)).ids.contains(conspiracy))
    assertEquals(replayed(f.ready, done.events), after)

  // ---- Lines ----

  private def seized(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(ConspiracyWhenPlayed.id, ConspiracyWhenPlayed.noteKeys,
      events)

  test("Conspiracy's line names the relic it seized and its owner"):
    val f = fromHand(p2AtAncientCity.relic(p2, crackedHorn, facedown = true))
    val done = take(f, atTarget(f), DecisionOptionRef.RelicSlot(p2, 0))
    assertEquals(seized(done.events), Vector(NoteText.Said("seized",
      s"p1 seized ${crackedHorn.value} from p2.", covers = false)))

  test("Conspiracy's line names the banner it seized"):
    val f = fromHand(p2AtAncientCity.darkestSecret(Some(p2), 3))
    val done = take(f, atTarget(f),
      DecisionOptionRef.Banner(Banner.DarkestSecret))
    assertEquals(seized(done.events), Vector(NoteText.Said("seized",
      "p1 seized Darkest Secret from p2.", covers = false)))

  test("a Conspiracy with no target writes no line"):
    val f = fromHand(Table.start)
    val done = Outcomes.finished(placedAs(f, PlayedAs.Faceup))
    assertEquals(seized(done.events), Vector.empty)
