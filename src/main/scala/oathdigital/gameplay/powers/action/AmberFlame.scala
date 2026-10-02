package oathdigital.gameplay.powers.action

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object AmberFlameCard extends Relic(RelicId("R32"), "Amber Flame", value = 26, defense = 2):
  val power = PrintedPower(PowerId("relic.amber-flame"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Burn [favor-burnt] or [secret-burnt] from a " +
      "banner held by a player whose pawn is at your site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Amber Flame (relic R32), ACTION: place 1 secret on this relic, then burn 1
  * favor or 1 secret from a banner held by a player whose pawn is at your
  * site.
  *
  * The banners are Book of Records' read, taken live after the cost is paid:
  * each one held by a player at the player's site, the player's own
  * included. The People's Favor burns a favor and the Darkest Secret a
  * secret. An empty banner is still a legal choice and burns nothing. With
  * no banner held there, the cost stays paid and one line says so.
  */
case object AmberFlame extends PaidAction(AmberFlameCard.power):
  val decisionId: String = "power.amber-flame.banner"
  val Burned: Int = 1
  /** "{Red} burned {1 favor} from {Blue}'s {People's Favor}." */
  val burned: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{Blue}'s {banner} held nothing to burn." */
  val empty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text("'s "), NotePart.Arg(1),
    NotePart.Text(" held nothing to burn.")))
  /** "No player at the site held a banner." */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player at the site held a banner.")))
  override def noteKeys: Vector[NoteKey] = Vector(burned, empty, nobody)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    BuildOps((live, pending) => burn(live, player, pending)),
    Note(id, burnedNote(_, player, source)))))

  private def ask(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    BookOfRecords.banners(ready, actor) match
      case Vector() =>
        Vector(Note(id, _ => PowerSourceRef.of(source).map(nobody(_))))
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(banner => DecisionOption.Banner(
          DecisionOptionRef.Banner(banner))),
        heading = Some("Amber Flame: burn from a banner held at your site"))))

  private def burn(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = BookOfRecords.banners(ready, actor)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      banner <- found.find(DecisionOptionRef.Banner(_) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a banner held at the actor's site"))
    yield
      val from = PositionedLocation(Location.OnBanner(banner))
      val burnt: CoreOperation = banner match
        case Banner.PeoplesFavor => Burn.favor(Burned, from)
        case Banner.DarkestSecret => Burn.secrets(Burned, from)
      if BannerRules.resources(ready.game.current, banner) == 0 then
        Vector.empty
      else Vector(burnt)

  /** What the chosen banner lost in the step before the note, and whose it
    * is. No answer means no banner was held at the site, whose line the
    * `Branch` already wrote. */
  private def burnedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    case DecisionOptionRef.Banner(banner) <-
      NoteSupport.answer(states, decisionId)
    holder <- BannerRules.holder(states.now.game.current, banner)
  yield
    val lost = states.previous.fold(0)((before, after) =>
      BannerRules.resources(before.game.current, banner) -
        BannerRules.resources(after.game.current, banner))
    val unit = banner match
      case Banner.PeoplesFavor => NoteUnit.Favor
      case Banner.DarkestSecret => NoteUnit.Secret
    if lost > 0 then burned(card, NoteArg.Player(actor),
      NoteArg.Amount(lost, unit), NoteArg.Player(holder),
      NoteArg.Banner(banner))
    else empty(card, NoteArg.Player(holder), NoteArg.Banner(banner))
