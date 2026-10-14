package oathdigital.gameplay.powers.burn

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

object VowOfSilenceCard extends Denizen(DenizenId("74"), "Vow of Silence", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-silence"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** challenge the Darkest Secret. Whenever an " +
      "enemy burns [secret-burnt], you take those secrets.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfDivisionCard extends Denizen(DenizenId("86"), "Vow of Division", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-division"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** take the Challenge action. Whenever an enemy " +
      "burns [favor-burnt], you take that favor unless it was " +
      "burned by a raid targeting you.")
  val powers: Vector[PrintedPower] = Vector(power)

/** A vow that takes what its holder's enemies burn of one resource
  * (catalog batch 8, slice 5), and bars a Challenge. The holder is the
  * player who holds the card faceup as an adviser; the card is
  * adviser-only, so no other place counts.
  *
  * It claims every burn of its resource by a player other than the holder,
  * and the walker replaces the burn with a `Take` to the holder
  * (`BurnTakes`). Every other player is an enemy, and the holder's own
  * burns go to the bank. Its Challenge half is each Vow's own.
  */
sealed abstract class BurnVow extends ContributingPower:
  def cardId: DenizenId
  /** "Took {Red}'s 2 burned secrets." */
  def taken: NoteKey
  /** "{Blue} took {Red}'s 2 burned secrets." */
  def takenBy: NoteKey
  /** Whether `resource` is the one it takes. */
  protected def takes(resource: Piece.Counted): Boolean

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** The player holding the card faceup, if any. */
  final def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case card: DenizenState =>
        card.id == cardId && card.orientation == Orientation.FaceUp
      case _ => false
    }).map(_.player)

  final override def takesBurn(ready: ReadyGame, burnt: Burnt)
      : Option[BurnTake] =
    holderOf(ready).filter(holder =>
      holder != burnt.by && takes(burnt.resource)).map(holder =>
      BurnTake(holder, line(holder)))

  /** What `holder` took from `burner`, naming the holder unless it is
    * their turn. */
  private def line(holder: PlayerId)(burner: PlayerId, amount: Int,
      actor: PlayerId): PowerNote =
    val card = PowerSourceRef.Card(cardId)
    if holder == actor then
      taken(card, NoteArg.Player(burner), NoteArg.Number(amount))
    else takenBy(card, NoteArg.Player(holder), NoteArg.Player(burner),
      NoteArg.Number(amount))

object BurnVow:
  /** "Took {Red}'s {2} burned {secrets}." */
  def taken(notes: NoteSet, one: String, many: String): NoteKey =
    notes.key("taken", Vector(
    NotePart.Text("Took "), NotePart.Arg(0), NotePart.Text("'s "),
    NotePart.Arg(1), NotePart.Text(" burned "),
    NotePart.Plural(1, one, many), NotePart.Text(".")))

  /** "{Blue} took {Red}'s {2} burned {secrets}." */
  def takenBy(notes: NoteSet, one: String, many: String): NoteKey =
    notes.key("taken-by",
    Vector(NotePart.Arg(0), NotePart.Text(" took "), NotePart.Arg(1),
      NotePart.Text("'s "), NotePart.Arg(2), NotePart.Text(" burned "),
      NotePart.Plural(2, one, many), NotePart.Text(".")))

/** Vow of Silence (74): "You cannot challenge the Darkest Secret. Whenever
  * an enemy burns [secret-burnt], you take those secrets."
  *
  * Its holder's banner choice keeps the Darkest Secret, unavailable, with
  * the reason; when it is the only banner they could challenge, the
  * Challenge cannot start. */
case object VowOfSilence extends BurnVow:
  val cardId: DenizenId = VowOfSilenceCard.id
  val id: PowerId = VowOfSilenceCard.power.id
  private val notes = NoteSet(id)
  val taken: NoteKey = BurnVow.taken(notes, "secret", "secrets")
  val takenBy: NoteKey = BurnVow.takenBy(notes, "secret", "secrets")
  protected def takes(resource: Piece.Counted): Boolean =
    resource.isInstanceOf[Piece.Secrets]

  /** "Cannot challenge the {Darkest Secret}.": the banner's hover
    * reason. */
  val forbidden: NoteKey = notes.key("forbidden", Vector(
    NotePart.Text("Cannot challenge the "), NotePart.Arg(0),
    NotePart.Text(".")))

  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ChallengeBannerSelection ->
      Vector(OptionRestriction(guard, reason)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match
    case DecisionOptionRef.Banner(Banner.DarkestSecret)
        if holderOf(ctx.state).contains(ctx.activePlayer) =>
      Some(OathViolation.StartUnavailable(
        "Vow of Silence prevents you from challenging the Darkest Secret"))
    case _ => None

  private def reason(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] = ref match
    case DecisionOptionRef.Banner(Banner.DarkestSecret) =>
      Some(forbidden(PowerSourceRef.Card(cardId),
        NoteArg.Banner(Banner.DarkestSecret)))
    case _ => None

/** Vow of Division (86): "You cannot take the Challenge action. Whenever an
  * enemy burns [favor-burnt], you take that favor unless it was burned by a
  * raid targeting you."
  *
  * A Raid's half-favor burn is the defeated defender's own, so the
  * exception needs no rule of its own: a defender holding the Vow is not
  * their own enemy. Its holder's Challenge is refused at the Challenge's
  * root, as Vow of Peace refuses a Campaign. */
case object VowOfDivision extends BurnVow:
  val cardId: DenizenId = VowOfDivisionCard.id
  val id: PowerId = VowOfDivisionCard.power.id
  private val notes = NoteSet(id)
  val taken: NoteKey = BurnVow.taken(notes, "favor", "favor")
  val takenBy: NoteKey = BurnVow.takenBy(notes, "favor", "favor")
  protected def takes(resource: Piece.Counted): Boolean =
    resource.isInstanceOf[Piece.Favor]

  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ChallengeActionEligibility -> Vector(Restriction((ctx, _) =>
      Option.when(holderOf(ctx.state).contains(ctx.activePlayer))(
        OathViolation.StartUnavailable(
          "Vow of Division prevents you from challenging")))))
