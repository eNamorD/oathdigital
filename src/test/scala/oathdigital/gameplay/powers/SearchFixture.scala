package oathdigital.gameplay.powers

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Drives a real Search through the rules, with the production walker powers,
  * for the suites of the modifiers and triggers that act on it. It builds on
  * `PowerFixture`; the first game deals only some cards, so a test names the
  * cards it needs and the fixture arranges the deck.
  */
object SearchFixture {
  import PowerFixture._

  val rules: OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  def after(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  /** The pawn site with nothing at it (its cards go to the bottom of the world
    * deck, its edifice to the edifice deck), the world deck topped by `top`,
    * the actor in the Act phase with `supply` Supply.
    */
  def staged(top: Vector[WorldCardId], supply: Int = 5): ReadyGame = {
    val siteId = home(base)
    val ready = base.updateCurrent { current =>
      val site = current.map.sites(siteId)
      val cards = site.denizens.collect { case d: DenizenState => d.id }
      val edifices = site.denizens.collect { case e: EdificeState => e.id }
      current.copy(
        commonCards = current.commonCards.copy(
          worldDeck = top ++ (current.commonCards.worldDeck.filterNot(top.contains) ++ cards),
          edificeDeck = current.commonCards.edificeDeck ++ edifices),
        map = current.map.copy(sites = current.map.sites.updated(siteId,
          site.copy(denizens = Vector.empty))))
    }
    inPhase(withBoard(ready)(_.copy(supply = SupplyTrack(supply))), Phase.Act)
  }

  /** The plain denizens of `suit` (unrestricted, with no production walker
    * power of their own) that no player and no site holds: in the world deck,
    * or not dealt at all. `staged` puts a card that is not dealt on top of the
    * deck.
    */
  def denizensOf(suit: Suit): Vector[DenizenId] = {
    val index = CardIndex.from(base.game).toOption.get
    val powered = WalkerPowerCatalog.default(catalog).powers.map(_.id).toSet
    catalog.denizens.filter(d => d.suit == suit &&
      !d.powers.exists(power => powered(power.id)) &&
      d.restrictions == oathdigital.catalog.CardRestrictions.Unrestricted)
      .map(d => DenizenId(d.id.value)).filter(id =>
        index.get(id).forall(_.location.container ==
          CardContainer.Deck(CardDeck.World)))
  }

  /** Starts a world Search with `modifiers`. */
  def start(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.Search, actor, modifiers,
      Vector(DecisionOptionRef.Button("search:world")))

  private def refOf(card: WorldCardId): DecisionOptionRef = card match {
    case id: DenizenId => DecisionOptionRef.Denizen(id)
    case id: VisionId => DecisionOptionRef.Vision(id)
  }

  /** Keeps `kept` from the drawn hand and discards the others. */
  def keep(from: OathTransition, kept: WorldCardId)
      : Either[OathViolation, OathTransition] = {
    val drawn = after(from).game.current.temporaryHands(actor)
    if (drawn.size == 1) Right(from.copy(events = Vector.empty))
    else rules.resolveWalker(from.state, actor, SearchProcedure.cardDecisionId,
      DecisionAnswer.PartitionAnswer(DecisionPlacement(refOf(kept),
        SearchProcedure.keepKey) +: drawn.filterNot(_ == kept).map(card =>
        DecisionPlacement(refOf(card), SearchProcedure.discardKey))))
  }

  /** Places the card with the placement button. */
  def place(from: OathTransition, card: WorldCardId, button: String)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor,
      s"cardplay.place.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(button)))

  /** A whole Search: start, keep `kept`, place it with `button`. The events are
    * those of the whole Search, so a replay can start from `ready`.
    */
  def play(ready: ReadyGame, modifiers: Vector[PowerId], kept: WorldCardId,
      button: String): OathTransition = (for {
    started <- start(ready, modifiers)
    chosen <- keep(started, kept)
    placed <- place(chosen, kept, button)
  } yield placed.copy(events = started.events ++ chosen.events ++
    placed.events)).fold(error => throw new AssertionError(error.toString),
    identity)

  /** The Play-Facedown-Adviser action: the actor plays `card`, which they hold
    * as a facedown adviser, with `button`. The events are those of the whole
    * action.
    */
  def playFacedown(ready: ReadyGame, modifiers: Vector[PowerId],
      card: DenizenId, button: String): OathTransition = (for {
    started <- rules.startWalker(Ready(ready), ActionRef.PlayFacedownAdviser,
      actor, modifiers, Vector(DecisionOptionRef.Denizen(card)))
    placed <- place(started, card, button)
  } yield placed.copy(events = started.events ++ placed.events)).fold(
    error => throw new AssertionError(error.toString), identity)

  /** Answers the replacement decision of a play with `chosen`. */
  def replace(from: OathTransition, card: WorldCardId, chosen: CardId)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor,
      s"cardplay.replace.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(chosen match {
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
        case other => DecisionOptionRef.Button(
          s"replace:${other.kind}:${other.value}")
      }))
}
