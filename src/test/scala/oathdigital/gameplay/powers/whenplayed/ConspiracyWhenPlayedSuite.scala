package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.{BannerRules, VisionRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

class ConspiracyWhenPlayedSuite extends munit.FunSuite {
  private val powers = WalkerPowers(Vector(ConspiracyWhenPlayed))
  private val conspiracy = VisionRules.Conspiracy
  private val placeId = s"cardplay.place.${conspiracy.kind}.${conspiracy.value}"
  private val faceup = DecisionOptionRef.Button("adviser-faceup")

  private final case class Staged(ready: ReadyGame, actor: PlayerId,
      enemy: PlayerId, origin: CardPlayProcedure.Origin)

  /** The actor holds Conspiracy at `origin`. The enemy stands on the actor's
    * site unless `shared` is false, and holds `relics`. No banner has a holder
    * until `edit` gives one.
    */
  private def fixture(relics: Vector[RelicState] = Vector.empty,
      shared: Boolean = true,
      origin: CardPlayProcedure.Origin = CardPlayProcedure.Origin.TemporaryHand)
      (edit: (ReadyGame, PlayerId) => ReadyGame = (ready, _) => ready)
      : Staged = {
    val base = initialReady
    val current = base.game.current
    val actor = current.turn.activePlayer
    val site = current.players.find(_.player == actor).get.pawnSite
    val enemy = current.players.find(_.player != actor).get.player
    val fromHand = origin == CardPlayProcedure.Origin.TemporaryHand
    val staged = base.updateCurrent(_.copy(
      players = current.players.map { player =>
        if (player.player == enemy) player.copy(
          pawnSite = if (shared) site else None, relics = relics)
        else if (player.player == actor && !fromHand) player.copy(advisers =
          player.advisers :+ VisionState(conspiracy, Orientation.FaceDown))
        else player
      },
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = None),
        darkestSecret = current.banners.darkestSecret.copy(holder = None)),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == conspiracy)),
      temporaryHands = if (fromHand)
        current.temporaryHands.updated(actor, Vector(conspiracy))
      else current.temporaryHands))
    Staged(edit(staged, enemy), actor, enemy, origin)
  }

  private def treeFor(f: Staged): Operation = (f.origin match {
    case CardPlayProcedure.Origin.TemporaryHand =>
      CardPlayProcedure.build(catalog, f.ready, f.actor, conspiracy, f.origin)
    case CardPlayProcedure.Origin.FacedownAdviser =>
      CardPlayProcedure.rebuildFacedown(catalog, f.ready, f.actor,
        Vector(DecisionOptionRef.Vision(conspiracy)))
  }).toOption.get

  private def parked(outcome: Either[OathViolation, WalkerOutcome]): PendingTree =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Parked].tree

  private def finished(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Finished =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Finished]

  private def answer(f: Staged, tree: Operation, at: PendingTree, id: String,
      ref: DecisionOptionRef) =
    ProcedureWalker.resolve(f.ready, tree, at, Answered(id,
      DecisionAnswer.ChooseOneAnswer(ref), f.actor), powers)

  /** Plays Conspiracy faceup and returns the tree with the position parked on
    * the target decision.
    */
  private def atTarget(f: Staged): (Operation, PendingTree) = {
    val tree = treeFor(f)
    val place = parked(ProcedureWalker.advance(f.ready, tree, None, powers))
    (tree, parked(answer(f, tree, place, placeId, faceup)))
  }

  private def targetOptions(f: Staged, tree: Operation, at: PendingTree) =
    ProcedureWalker.parkedDecide(f.ready, tree, at, powers).map(decide =>
      (decide.decisionId, decide.owner, decide.query
        .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)))

  private def recorded(done: WalkerOutcome.Finished): Vector[CoreOperation] =
    done.events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  private def replayed(f: Staged, done: WalkerOutcome.Finished): ReadyGame =
    OperationPipeline.run(f.ready, recorded(done),
      OperationPolicy.Permissive)(Right(_)).toOption.get.state

  private def player(ready: ReadyGame, id: PlayerId): PlayerState =
    ready.game.current.players.find(_.player == id).get

  test("the default walker catalog carries the Conspiracy power") {
    assert(WalkerPowerCatalog.default(catalog).powers
      .contains(ConspiracyWhenPlayed))
  }

  test("Conspiracy takes an opaque relic slot and leaves the game") {
    val relic = RelicState(RelicId("conspiracy-relic"), Orientation.FaceDown,
      Tokens.empty)
    val f = fixture(Vector(relic))()
    val (tree, at) = atTarget(f)
    assertEquals(targetOptions(f, tree, at), Some((
      ConspiracyWhenPlayed.decisionId, f.actor,
      Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(f.enemy, 0)))))
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.RelicSlot(f.enemy, 0)))
    val after = done.treeless
    assert(player(after, f.actor).relics.exists(_.id == relic.id))
    assert(!player(after, f.enemy).relics.exists(_.id == relic.id))
    assertEquals(after.game.current.temporaryHands(f.actor), Vector.empty)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
    assertEquals(replayed(f, done), after)
  }

  test("Conspiracy taking the Peoples Favor returns its favor in the " +
      "least-bank order and takes the banner") {
    val f = fixture()((ready, enemy) => {
      val current = ready.game.current
      ready.copy(banks = ready.banks.copy(favor = ready.banks.favor.map {
        case (suit, count) => suit -> math.max(0, count - 2) }))
        .updateCurrent(_.copy(banners = current.banners.copy(peoplesFavor =
          current.banners.peoplesFavor.copy(holder = Some(enemy), favor = 2))))
    })
    val (tree, at) = atTarget(f)
    assertEquals(targetOptions(f, tree, at), Some((
      ConspiracyWhenPlayed.decisionId, f.actor,
      Vector[DecisionOptionRef](DecisionOptionRef.Banner(Banner.PeoplesFavor)))))
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    val after = done.treeless
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(f.actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(after.banks.favor.values.sum, f.ready.banks.favor.values.sum + 2)
    val returned = recorded(done).collect {
      case Move(Piece.Favor(1), _,
          PositionedLocation(Location.FavorBank(suit), _), _) => suit
    }
    assertEquals(returned, BannerRules.raidFavorReturn(f.ready.banks.favor, 2))
    assertEquals(after.game.current.temporaryHands(f.actor), Vector.empty)
    assertEquals(replayed(f, done), after)
  }

  test("Conspiracy taking the Darkest Secret burns every secret and takes " +
      "the banner") {
    val f = fixture()((ready, enemy) => {
      val current = ready.game.current
      ready.updateCurrent(_.copy(banners = current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = Some(enemy), secrets = 3))))
    })
    def siteSecrets(ready: ReadyGame): Int =
      ready.game.current.map.sites.valuesIterator.map { site =>
        site.tokens.secrets + site.denizens.collect {
          case card: DenizenState => card.tokens.secrets
        }.sum
      }.sum
    val (tree, at) = atTarget(f)
    val done = finished(answer(f, tree, at, ConspiracyWhenPlayed.decisionId,
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    val after = done.treeless
    assertEquals(after.game.current.banners.darkestSecret.secrets, 0)
    assertEquals(after.game.current.banners.darkestSecret.holder, Some(f.actor))
    // The burn returns the secrets to the untracked shared bank; none lands on
    // a site.
    assertEquals(siteSecrets(after), siteSecrets(f.ready))
    assertEquals(replayed(f, done), after)
  }

  test("with no legal target Conspiracy asks nothing and only leaves the game") {
    val f = fixture(shared = false)()
    val tree = treeFor(f)
    val place = parked(ProcedureWalker.advance(f.ready, tree, None, powers))
    val done = finished(answer(f, tree, place, placeId, faceup))
    val after = done.treeless
    assertEquals(after.game.current.temporaryHands(f.actor), Vector.empty)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
    assertEquals(recorded(done), Vector[CoreOperation](Move(
      Piece.Card(conspiracy), PositionedLocation(Location.Hand(f.actor)),
      PositionedLocation(Location.SharedBank))))
  }

  test("a target the current state does not offer is rejected at the decision") {
    val relics = Vector("first", "second").map(id => RelicState(
      RelicId(s"conspiracy-$id"), Orientation.FaceDown, Tokens.empty))
    val f = fixture(relics)()
    val (tree, at) = atTarget(f)
    assertEquals(targetOptions(f, tree, at).map(_._3), Some(Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(f.enemy, 0),
      DecisionOptionRef.RelicSlot(f.enemy, 1))))
    Vector[DecisionOptionRef](DecisionOptionRef.RelicSlot(f.enemy, 2),
      DecisionOptionRef.RelicSlot(f.actor, 0),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)).foreach { ref =>
      assert(answer(f, tree, at, ConspiracyWhenPlayed.decisionId, ref).isLeft,
        ref.toString)
    }
  }

  test("discarding a Conspiracy does not play it") {
    val relic = RelicState(RelicId("kept-relic"), Orientation.FaceDown,
      Tokens.empty)
    val f = fixture(Vector(relic))()
    val tree = treeFor(f)
    val place = parked(ProcedureWalker.advance(f.ready, tree, None, powers))
    val done = finished(answer(f, tree, place, placeId,
      DecisionOptionRef.Button("discard")))
    assert(player(done.treeless, f.enemy).relics.exists(_.id == relic.id))
    assert(CardIndex.from(done.treeless.game).toOption.get.ids
      .contains(conspiracy))
  }
}
