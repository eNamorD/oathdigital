package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, RelicDraws}
import oathdigital.model._

/** Fae Merchant (card 180), ACTION: place 1 secret on this card, draw a relic
  * and take it facedown, then put exactly one relic you hold, except the
  * Grand Scepter, on the bottom of the relic deck. The relic just taken is
  * eligible.
  *
  * Three siblings run in order: the draw, a live `Branch` that holds only the
  * decision and reads the relics after the draw, and the bury. The decision
  * offers every relic held, so a lone relic is confirmed with one click. The
  * Grand Scepter's restriction refuses its bury, and the bury is a required
  * batch, so the search hides the scepter. The decision is passed when the
  * search leaves it empty (`Decide.passWhenEmpty`): with only the scepter held
  * and the relic deck empty, nothing goes back. The bury returns any secrets
  * on the relic to their holder. Its `returned` line names the relic chosen,
  * so the choice posts no "Chose" line.
  */
case object FaeMerchant extends PaidAction("denizen.fae-merchant",
    Cost(secret = 1)):
  val decisionId: String = "fae-merchant.relic"
  val returned: NoteKey = NoteKey("returned", Vector(NotePart.Arg(0),
    NotePart.Text(" put "), NotePart.Arg(1),
    NotePart.Text(" on the bottom of the relic deck.")))

  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, returned)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Note(this.id, RelicDraws.drawNote(source, player)),
    Branch((state, _) => held(state, player) match {
      case Vector() => Vector.empty
      case relics => Vector(Decide(decisionId, player,
        DecisionQuery.ChooseOne(relics.map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
          heading = Some("Fae Merchant: put a relic on the bottom of the " +
            "relic deck")),
        passWhenEmpty = true))
    }),
    BuildOps((state, pending) => putBack(state, player, pending),
      required = true),
    Note(this.id, returnNote(_, player, source), covers = true))))

  /** The relic the bury took from the player, in place of its Buried line. */
  private def returnNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsLost(step, player).headOption
  yield returned(card, NoteArg.Player(player), NoteArg.Card(relic))

  /** The relics the player holds, in play-area order. */
  private def held(state: ReadyGame, player: PlayerId): Vector[RelicId] =
    PlayerFacts.player(state, player).toOption.toVector.flatMap(_.relics)
      .map(_.id)

  /** Buries the chosen relic. No answer means the search left the decision
    * no relic to offer, so nothing goes back. */
  private def putBack(state: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val chosen = pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Relic(id)), _) => id
    }
    PlayerFacts.player(state, player).map(holder => chosen.toVector.flatMap {
      id =>
        val secrets = holder.relics.find(_.id == id).fold(0)(_.tokens.secrets)
        Bury.standard(BuryableCard.Relic(id),
          PositionedLocation(Location.PlayArea(player)), None, 0, secrets,
          player)
    })
