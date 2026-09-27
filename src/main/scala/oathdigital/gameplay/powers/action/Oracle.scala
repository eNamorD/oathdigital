package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Oracle (denizen 160), site-only, ACTION: place 2 secrets on this card,
  * then draw the Vision closest to the top of the world deck and play or
  * discard it as if you searched.
  *
  * The Vision is taken from wherever it lies, into the player's temporary
  * hand, and the Visions Drawn track advances. The cards above it stay in
  * place, unseen. The Vision is then placed through the same card-play
  * subtree a Search's kept card goes through, so the restrictions on a
  * faceup Vision and Search's placement modifiers apply. A deck with no
  * Vision draws nothing and asks nothing.
  *
  * The tree is rebuilt on every resume. The placement reads the Vision from
  * the temporary hand, or, once it is played, from the placement answer, as
  * a Search does.
  */
final case class Oracle private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.oracle", Cost(secret = 2)):
  import Oracle._

  override def noteKeys: Vector[NoteKey] = Vector(drew, noVision)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(first(live).toVector.flatMap(vision =>
      Vector[CoreOperation](Take(Piece.Card(vision), player,
        Location.Deck(CardDeck.World), Location.Hand(player)),
        AdvanceVisionsDrawn)))),
    Note(this.id, states => PowerSourceRef.of(source).map(card =>
      held(states.now, player).fold(noVision(card))(vision =>
        drew(card, NoteArg.Player(player), NoteArg.Card(vision))))),
    Branch((live, pending) => held(live, player)
      .orElse(CardPlayProcedure.placedCard(pending)).toVector.map(vision =>
        CardPlayProcedure.unchecked(catalog, live, player, vision,
          CardPlayProcedure.Origin.TemporaryHand))))))

object Oracle:
  val id: PowerId = PowerId("denizen.oracle")
  /** "{Red} drew {Vision} from the world deck." */
  val drew: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" drew "), NotePart.Arg(1),
    NotePart.Text(" from the world deck.")))
  /** "The world deck held no Vision." */
  val noVision: NoteKey = NoteKey("used.none",
    Vector(NotePart.Text("The world deck held no Vision.")))

  def forCatalog(catalog: ExecutableCatalog): Option[Oracle] =
    CatalogCards.denizen(catalog, id).map(_ => new Oracle(catalog))

  /** The Vision closest to the top of the world deck, stored top first. */
  private def first(ready: ReadyGame): Option[VisionId] =
    ready.game.current.commonCards.worldDeck.collectFirst {
      case vision: VisionId => vision }

  /** The Vision in the player's temporary hand: the one Oracle drew, until
    * it is played. */
  private def held(ready: ReadyGame, player: PlayerId): Option[VisionId] =
    ready.game.current.temporaryHands.getOrElse(player, Vector.empty)
      .collectFirst { case vision: VisionId => vision }
