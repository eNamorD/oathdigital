package oathdigital.gameplay.powers.banner

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.challenge.ChallengeProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.model._

object SecretTestamentCard extends Relic(RelicId("R38"), "Secret Testament", value = 82, defense = 2):
  val power = PrintedPower(PowerId("relic.secret-testament"),
    persistent = true, cost = Cost.free,
    text = "When an enemy takes a banner from you, take all [favor] or " +
      "[secret] on the banner.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Secret Testament (relic R38), a persistent Challenge modifier: "When an
  * enemy takes a banner from you, take all [favor] or [secret] on the
  * banner." (catalog batch 8 rulings, slice 6).
  *
  * When an enemy challenges for a banner its faceup holder holds, it
  * replaces the Challenge's ribbon: the holder takes every favor or secret
  * on the banner, in place of the favor's return to the banks, or the
  * Darkest Secret's retained half and the secrets placed on sites. The
  * challenger then pays onto the empty banner and takes it. A Campaign or a
  * card that takes a banner walks no ribbon, so it changes nothing there.
  *
  * Its line names the holder, whose turn it never is: "{Red} took 2 favor
  * from the {People's Favor}." An empty banner writes none.
  */
case object SecretTestament extends ContributingPower:
  val cardId: RelicId = SecretTestamentCard.id
  val id: PowerId = SecretTestamentCard.power.id

  private val notes = NoteSet(id)
  /** "{Red} took 2 favor from the {People's Favor}." */
  val tookFavor: NoteKey = notes.key("took-favor", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" favor from the "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} took 2 secrets from the {Darkest Secret}." */
  val tookSecrets: NoteKey = notes.key("took-secrets", Vector(
    NotePart.Arg(0), NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" "), NotePart.Plural(1, "secret", "secrets"),
    NotePart.Text(" from the "), NotePart.Arg(2), NotePart.Text(".")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ChallengeRibbon -> Vector(Transform((ctx, children) =>
      challenged(ctx).fold(children)((banner, holder) => Vector(
        BuildOps((ready, _) => Right(taken(ready, banner, holder))),
        Note(id, line(banner, holder)))))))

  /** The banner an enemy of its holder challenges for, and that holder. */
  private def challenged(ctx: PowerCtx): Option[(Banner, PlayerId)] = for
    banner <- ctx.answered.collectFirst {
      case Answered(ChallengeProcedure.bannerDecisionId,
          DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Banner(chosen)),
          _) => chosen }
    holder <- BannerRules.holder(ctx.state.game.current, banner)
    if holder != ctx.activePlayer && holderOf(ctx.state).contains(holder)
  yield (banner, holder)

  /** Every favor or secret on `banner`, moved to `holder`. */
  private def taken(ready: ReadyGame, banner: Banner, holder: PlayerId)
      : Vector[CoreOperation] =
    val amount = BannerRules.resources(ready.game.current, banner)
    Option.when(amount > 0)(Move(piece(banner, amount),
      PositionedLocation(Location.OnBanner(banner)),
      PositionedLocation(Location.PlayArea(holder))): CoreOperation).toVector

  private def piece(banner: Banner, amount: Int): Piece = banner match
    case Banner.PeoplesFavor => Piece.Favor(amount)
    case Banner.DarkestSecret => Piece.Secrets(amount)

  /** What the holder took, read from the banner before the take. */
  private def line(banner: Banner, holder: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    amount = BannerRules.resources(step._1.game.current, banner)
    if amount > 0
  yield
    val key = banner match
      case Banner.PeoplesFavor => tookFavor
      case Banner.DarkestSecret => tookSecrets
    key(PowerSourceRef.Card(cardId), NoteArg.Player(holder),
      NoteArg.Number(amount), NoteArg.Banner(banner))

  /** The player holding the relic faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)
