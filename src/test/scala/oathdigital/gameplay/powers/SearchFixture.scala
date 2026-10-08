package oathdigital.gameplay.powers

import oathdigital.gameplay.{FacedownAdviserPlay, OathRules, PlacementFixture,
  SearchPlay}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Outcomes, PlacementPicks, PowerRules,
  Table, TestCards}
import oathdigital.testkit.Unwrap.accepted

/** Drives a real Search through the rules, with the production walker powers,
  * for the suites of the modifiers and triggers that act on it. It builds on
  * `PowerFixture`; the first game deals only some cards, so a test names the
  * cards it needs and the fixture arranges the deck.
  */
object SearchFixture:
  import PowerFixture._

  private val walkerPowers: WalkerPowers = WalkerPowerCatalog.default(catalog)

  val rules: OathRules = PowerRules.withWalkerPowers

  def after(transition: OathTransition)(using munit.Location): ReadyGame =
    Outcomes.ready(transition.state)

  /** The quiet table's p1 in Act at an empty Ancient City, the world deck
    * topped by `top`, with `supply` Supply.
    */
  def staged(top: Vector[WorldCardId], supply: Int): ReadyGame =
    Table.start.worldDeckTop(top*).supply(actor, supply).ready

  /** Eight Discord denizens for the world deck. None is played faceup, so
    * no power of theirs applies.
    */
  val discordCards: Vector[DenizenId] = Vector("Defame", "Second Wind",
    "Scryer", "Bandit Paymaster", "Unstable Summon", "Dark Enforcer",
    "Spoiled Supplies", "Blackmail").map(CatalogNames.denizen(_))

  /** Any plain denizens of `suit` (unrestricted, with no production walker
    * power of their own) that no player and no site holds: in the world deck,
    * or not dealt at all. `staged` puts a card that is not dealt on top of the
    * deck. For a test where the card is arbitrary; a test that rests on the
    * card names it.
    */
  def anyPlainDenizensOf(suit: Suit): Vector[DenizenId] =
    val index = accepted(CardIndex.from(quietTable.game))
    val powered = WalkerPowerCatalog.default(catalog).powers.map(_.id).toSet
    catalog.denizens.filter(d => d.suit == suit &&
      !d.powers.exists(power => powered(power.id)) &&
      TestCards.unrestricted(d))
      .map(d => DenizenId(d.id.value)).filter(id =>
        index.get(id).forall(_.location.container ==
          CardContainer.Deck(CardDeck.World)))

  /** Starts a Search with `modifiers` and picks the world deck. */
  def start(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    SearchPlay.searched(rules, Ready(ready), actor, SearchSource.WorldDeck,
      modifiers)

  private def refOf(card: WorldCardId): DecisionOptionRef = card match
    case id: DenizenId => DecisionOptionRef.Denizen(id)
    case id: VisionId => DecisionOptionRef.Vision(id)

  /** Keeps `kept` from the drawn hand and discards the others. With only one
    * card drawn there is no question, and the transition comes back with no
    * events of its own. */
  def keep(from: OathTransition, kept: WorldCardId)
      : Either[OathViolation, OathTransition] =
    val drawn = after(from).game.current.temporaryHands(actor)
    if drawn.size == 1 then Right(from.copy(events = Vector.empty))
    else rules.resolveWalker(from.state, actor, SearchProcedure.cardDecisionId,
      DecisionAnswer.PartitionAnswer(DecisionPlacement(refOf(kept),
        SearchProcedure.keepKey) +: drawn.filterNot(_ == kept).map(card =>
        DecisionPlacement(refOf(card), SearchProcedure.discardKey))))

  /** Places the card the way `way` names: the parked placement's first
    * option that plays it so, preferring one that may be chosen. A placement
    * with no such option refuses, as the engine would. */
  def place(from: OathTransition, card: WorldCardId, way: PlayedAs)(
      using munit.Location): Either[OathViolation, OathTransition] =
    PlacementPicks.find(PlacementPicks.parkedOptions(from.state, walkerPowers),
        way).toRight(OathViolation.InvalidSearchPlacement(
        s"the placement offers no way to play the card $way"))
      .flatMap(ref => rules.resolveWalker(from.state, actor,
        PlacementFixture.cardPlayDecisionId(card, "place"),
        DecisionAnswer.ChooseOneAnswer(ref)))

  /** A whole Search: start, keep `kept`, place it the way `way` names. The
    * events are those of the whole Search, so a replay can start from `ready`.
    */
  def play(ready: ReadyGame, modifiers: Vector[PowerId], kept: WorldCardId,
      way: PlayedAs)(using munit.Location): OathTransition = (for
    started <- start(ready, modifiers)
    chosen <- keep(started, kept)
    placed <- place(chosen, kept, way)
  yield placed.copy(events = started.events ++ chosen.events ++
    placed.events)).fold(error => throw new AssertionError(error.toString),
    identity)

  /** The Play-Facedown-Adviser action: the actor plays `card`, which they hold
    * as a facedown adviser, the way `way` names. The events are those of the
    * whole action.
    */
  def playFacedown(ready: ReadyGame, modifiers: Vector[PowerId],
      card: DenizenId, way: PlayedAs)(using munit.Location)
      : OathTransition = (for
    started <- FacedownAdviserPlay.parkedOnPlacement(rules, Ready(ready),
      actor, card, modifiers)
    placed <- place(started, card, way)
  yield placed.copy(events = started.events ++ placed.events)).fold(
    error => throw new AssertionError(error.toString), identity)

  /** Places the card `way` in the place of `replaced`: the Replace option
    * that discards it, whether or not it may be chosen. */
  def placeReplacing(from: OathTransition, card: WorldCardId, way: PlayedAs,
      replaced: CardId)(using munit.Location)
      : Either[OathViolation, OathTransition] =
    PlacementPicks.findReplacing(PlacementPicks.parkedOptions(from.state,
        walkerPowers), way, replaced).toRight(
        OathViolation.InvalidSearchPlacement(
          s"no option plays the card $way in the place of $replaced"))
      .flatMap(ref => rules.resolveWalker(from.state, actor,
        PlacementFixture.cardPlayDecisionId(card, "place"),
        DecisionAnswer.ChooseOneAnswer(ref)))

  /** Answers the follow-up of a Replace site slot: discard `more` as well. */
  def discardMore(from: OathTransition, card: WorldCardId,
      more: Vector[CardId]): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor,
      PlacementFixture.cardPlayDecisionId(card, "discard"),
      DecisionAnswer.ChooseManyAnswer(more.map {
        case id: EdificeId => DecisionOptionRef.Edifice(id)
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case other => DecisionOptionRef.Button(other.value)
      }))
