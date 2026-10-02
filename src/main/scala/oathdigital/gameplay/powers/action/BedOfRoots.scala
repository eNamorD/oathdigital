package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts,
  PowerAnswers}
import oathdigital.model._

object BedOfRootsCard extends Denizen(DenizenId("212"), "Bed of Roots", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.bed-of-roots"),
    persistent = false, cost = Cost(favorBurnt = 3),
    text = "**ACTION:** Bury a faceup adviser you have _(even if " +
      "locked)_ to gain [secret] [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bed of Roots (card 212, site-only), ACTION: burn 3 favor, then bury a
  * faceup adviser you have, even if locked, to gain 2 secrets.
  *
  * The candidates are the player's faceup denizen advisers, in adviser
  * order. A Vision is not an adviser of that kind, and a facedown adviser is
  * not faceup. `Bury` ignores locked. The question is asked whenever there
  * is a candidate, even one. The bury and the gain run as one required
  * batch, so the gain comes only with the bury. The bury uses the standard
  * returns: favor to the card's suit bank, secrets to the player facedown.
  * With no candidate the cost stays paid and nothing else happens.
  *
  * Its line names the card and the secrets gained, not those returned, and
  * covers the generic Buried and gain lines.
  */
final case class BedOfRoots private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.bed-of-roots", Cost(favorBurnt = 3)):
  import BedOfRoots._

  override def noteKeys: Vector[NoteKey] = Vector(buried, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending), required = true),
    Note(this.id, buriedNote(_, player, source), covers = true))))

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    candidates(ready, player) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
        found.map(card => DecisionOption.Denizen(
          DecisionOptionRef.Denizen(card.id))),
        heading = Some("Bed of Roots: bury one of your faceup advisers to " +
          "gain 2 secrets")), passWhenEmpty = true))

  /** No answer means the player had no faceup adviser to offer. */
  private def bury(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) =>
        for
          chosen <- candidates(ready, player)
            .find(card => DecisionOptionRef.Denizen(card.id) == ref)
            .toRight(OathViolation.InvalidEventOrder(
              s"${ref.wireId} is not a faceup adviser Bed of Roots can bury"))
          suit = catalog.suitOf(chosen.id)
          _ <- Either.cond(chosen.tokens.favor == 0 || suit.isDefined, (),
            OathViolation.InvalidEventOrder(
              s"no suit is known for ${chosen.id.value}"))
        yield Bury.standard(BuryableCard.Denizen(chosen.id),
          PositionedLocation(Location.PlayArea(player)), suit,
          chosen.tokens.favor, chosen.tokens.secrets, player) :+
          Gain.Secrets(player, Gained)

  /** The adviser the step before the note took from the player, read where
    * it stood before. No adviser taken means there was none to bury. */
  private def buriedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      step <- states.previous
      chosen <- candidates(step._1, player).find(before =>
        !candidates(step._2, player).exists(_.id == before.id))
    yield buried(card, NoteArg.Player(player), NoteArg.Card(chosen.id),
      NoteArg.Amount(NoteSupport.secrets(step, player) -
        chosen.tokens.secrets, NoteUnit.Secret)))
      .getOrElse(bare(card, NoteArg.Player(player))))

object BedOfRoots:
  val id: PowerId = PowerId("denizen.bed-of-roots")
  val decisionId: String = "power.bed-of-roots.adviser"
  val Gained: Int = 2
  /** "{Red} buried {card} and gained {n secrets}." */
  val buried: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} had no faceup adviser." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no faceup adviser.")))

  /** The player's faceup denizen advisers, in adviser order. */
  private def candidates(ready: ReadyGame, player: PlayerId)
      : Vector[DenizenState] =
    PlayerFacts.player(ready, player).toOption.toVector.flatMap(_.advisers
      .collect { case card @ DenizenState(_, Orientation.FaceUp, _) => card })

  def forCatalog(catalog: ExecutableCatalog): Option[BedOfRoots] =
    CatalogCards.denizen(catalog, id).map(_ => new BedOfRoots(catalog))
