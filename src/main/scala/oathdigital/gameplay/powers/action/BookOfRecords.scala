package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Book of Records (relic R19), ACTION: place 1 secret on this card and burn
  * 2, then take two favor or two secrets from a banner held by a player whose
  * pawn is at the player's site, the player's own banner included. The
  * People's Favor gives favor and the Darkest Secret gives secrets, up to two
  * of what it holds.
  *
  * The banners are read live, after the cost is paid. With none held at the
  * site the cost stays paid and one line says so. An empty banner is still a
  * legal choice and gives nothing. The take is a `Take`, so a restriction on
  * taking applies to it.
  */
case object BookOfRecords extends PaidAction("relic.book-of-records",
    Cost(secret = 1, secretBurnt = 2)):
  val decisionId: String = "power.book-of-records.banner"
  val Most: Int = 2
  /** "{Red} took {2 favor} from {Blue}'s {People's Favor}." */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{Blue}'s {banner} held nothing to take." */
  val empty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text("'s "), NotePart.Arg(1),
    NotePart.Text(" held nothing to take.")))
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player at the site held a banner.")))
  override def noteKeys: Vector[NoteKey] = Vector(took, empty, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    BuildOps((live, pending) => take(live, player, pending)),
    Note(id, tookNote(_, player, source)))))

  /** The banners held by a player whose pawn is at the actor's site. */
  private def banners(ready: ReadyGame, actor: PlayerId): Vector[Banner] =
    val current = ready.game.current
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      Banner.all.filter(banner => BannerRules.holder(current, banner)
        .exists(holder => current.players.exists(p =>
          p.player == holder && p.pawnSite.contains(site)))))

  private def ask(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    val found = banners(ready, actor)
    if found.isEmpty then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(nobody(_))))
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(banner => DecisionOption.Banner(DecisionOptionRef.Banner(banner))),
      heading = Some("Book of Records: take from a banner held at your site"))))

  private def piece(banner: Banner, amount: Int): Piece = banner match
    case Banner.PeoplesFavor => Piece.Favor(amount)
    case Banner.DarkestSecret => Piece.Secrets(amount)

  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = banners(ready, actor)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      banner <- found.find(DecisionOptionRef.Banner(_) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a banner held at the actor's site"))
    yield
      val amount = math.min(Most, BannerRules.resources(ready.game.current,
        banner))
      if amount == 0 then Vector.empty
      else Vector(Take(piece(banner, amount), actor, Location.OnBanner(banner),
        Location.PlayArea(actor)))

  /** What the take moved to the actor, from whose banner. No answer means no
    * banner was held at the site, whose line the `Branch` already wrote. */
  private def tookNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    banner <- NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Banner(chosen) => chosen }
    step <- states.previous
    holder <- BannerRules.holder(step._1.game.current, banner)
  yield
    val (amount, unit) = banner match
      case Banner.PeoplesFavor => (NoteSupport.favor(step, actor), NoteUnit.Favor)
      case Banner.DarkestSecret =>
        (NoteSupport.secrets(step, actor), NoteUnit.Secret)
    if amount > 0 then took(card, NoteArg.Player(actor),
      NoteArg.Amount(amount, unit), NoteArg.Player(holder),
      NoteArg.Banner(banner))
    else empty(card, NoteArg.Player(holder), NoteArg.Banner(banner))
