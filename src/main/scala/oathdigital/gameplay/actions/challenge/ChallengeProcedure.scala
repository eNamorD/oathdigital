package oathdigital.gameplay.actions.challenge

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{BannerRules, ProcedureNotes}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.walker.ModifierChoice
import oathdigital.model._

/** Challenge on the walker: spend one Supply, choose a banner, choose how many
  * resources replace it, return the banner's resources (the ribbon), then pay
  * the replacement and take the banner. Takes no start selection. It opens
  * with the modifier decision, which asks nothing while no power selects at
  * its window.
  *
  * The Supply is spent first so no decision reads it. The banner and the
  * amount are decided before any resource moves, so both read the board as
  * the player sees it; the ribbon and the payment are built when reached.
  */
object ChallengeProcedure:
  val bannerDecisionId: String = "challenge.banner"
  val amountDecisionId: String = "challenge.amount"

  /** "Start Challenge": the modifier decision's button. */
  val startLabel: String = "Start Challenge"

  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Challenge))
  /** "Took People's Favor from p2 with 2 favor". Favor never pluralises;
    * secrets do, which is why the unit is in the template and not an
    * argument. */
  val tookFromPlayerWithFavor: NoteKey = notes.key("took-from-player-favor",
    Vector(NotePart.Text("Took "), NotePart.Arg(0), NotePart.Text(" from "),
      NotePart.Arg(1), NotePart.Text(" with "), NotePart.Arg(2),
      NotePart.Text(" favor")))
  val tookFromBankWithFavor: NoteKey = notes.key("took-from-bank-favor",
    Vector(NotePart.Text("Took "), NotePart.Arg(0),
      NotePart.Text(" from the bank with "), NotePart.Arg(1),
      NotePart.Text(" favor")))
  val tookFromPlayerWithSecrets: NoteKey = notes.key(
    "took-from-player-secrets",
    Vector(NotePart.Text("Took "), NotePart.Arg(0), NotePart.Text(" from "),
      NotePart.Arg(1), NotePart.Text(" with "), NotePart.Arg(2),
      NotePart.Text(" "), NotePart.Plural(2, "secret", "secrets")))
  val tookFromBankWithSecrets: NoteKey = notes.key("took-from-bank-secrets",
    Vector(NotePart.Text("Took "), NotePart.Arg(0),
      NotePart.Text(" from the bank with "), NotePart.Arg(1),
      NotePart.Text(" "), NotePart.Plural(1, "secret", "secrets")))
  def noteKeys: Vector[NoteKey] = notes.declared

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- noStartArgs(args)
    _ <- afterModifiers(state, actor)
  yield tree(actor)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(actor))

  /** The Challenge itself, behind the start's gates. */
  private def afterModifiers(state: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Operation] = for
    _ <- supportedFaces(state)
    _ <- Either.cond(legalBanners(state, actor).nonEmpty, (),
      OathViolation.NoPlayableOption(ActionRef.Challenge.key))
  yield challenge(actor)

  /** The modifier decision, then the Challenge. No power selects at
    * `ChallengeModifierSelection` yet, so the decision asks nothing. */
  private def tree(actor: PlayerId): Operation =
    val rest = challenge(actor)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.ChallengeModifierSelection, rest, startLabel), rest))

  /** The banners the actor could challenge now, in the banners' declared
    * order: not already held by the actor, the holder (if any) at the actor's
    * site, and strictly more relevant faceup resources than the banner holds.
    * Supply is a cost owned by `SpendSupply`, not a fact about a banner.
    */
  def legalBanners(state: ReadyGame, actor: PlayerId): Vector[Banner] =
    val current = state.game.current
    current.players.find(_.player == actor).toVector.flatMap { player =>
      Banner.all.filter { banner =>
        val holder = BannerRules.holder(current, banner)
        !holder.contains(actor) &&
          holder.forall(rival => player.pawnSite.nonEmpty && player.pawnSite ==
            current.pawnSite(rival)) &&
          BannerRules.playerResources(player, banner) >
            BannerRules.resources(current, banner)
      }
    }

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.Challenge.key} takes no start selection, " +
        s"got ${args.map(_.kind).mkString(", ")}"))

  /** The ribbon rules below are the printed ones for People's Favor `Mob` and
    * Darkest Secret `WanderingFlame` only.
    */
  private def supportedFaces(state: ReadyGame): Either[OathViolation, Unit] =
    val banners = state.game.current.banners
    Either.cond(banners.peoplesFavor.active == PeoplesFavorFace.Mob &&
      banners.darkestSecret.active == DarkestSecretFace.WanderingFlame, (),
      OathViolation.UnsupportedBannerState("unsupported active banner face"))

  private def challenge(actor: PlayerId): Operation = Sequence(Vector[Operation](
    Sequence(Vector[Operation](SpendSupply(actor, 1)),
      Some(PowerWindow.ChallengeCost)),
    Branch((ready, _) => Vector(Decide(bannerDecisionId, actor,
      DecisionQuery.ChooseOne(legalBanners(ready, actor).map(banner =>
        DecisionOption.Banner(DecisionOptionRef.Banner(banner))),
        heading = Some("Choose a banner to Challenge"),
        confirmVerb = Some("Challenge")),
      window = Some(PowerWindow.ChallengeBannerSelection)))),
    Branch((ready, pending) => bannerOf(pending).toVector.map(banner =>
      amountDecision(ready, actor, banner))),
    Branch((ready, pending) => effects(ready, actor, pending))),
    Some(PowerWindow.ChallengeActionEligibility))

  private def amountDecision(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation =
    val current = ready.game.current
    val prior = BannerRules.resources(current, banner)
    val own = current.players.find(_.player == actor)
      .fold(0)(BannerRules.playerResources(_, banner))
    val unit = banner match
      case Banner.PeoplesFavor => "favor"
      case Banner.DarkestSecret => "secrets"
    Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(prior + 1,
      math.max(prior + 1, own),
      Some(s"Place more than $prior $unit to take ${banner.key}"),
      "Take banner"),
      window = Some(PowerWindow.ChallengeAmountSelection))

  /** Reached only after both answers exist. Selecting reads the two answers
    * and the banner's holder, which no earlier step changes; the ribbon and
    * the payment amount are built when walked.
    */
  private def effects(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    (for
      banner <- bannerOf(pending)
      amount <- amountOf(pending)
    yield Vector[Operation](
      Sequence(ChallengeRibbon.steps(actor, banner),
        Some(PowerWindow.ChallengeRibbon)),
      Sequence(Vector[Operation](payment(actor, banner, amount),
        // Directly after the payment, so `previous` is the payment and the
        // note reads what actually left the play area rather than the
        // amount answered. The holder is read here, before the take moves
        // the banner, which is the holder the deleted rule read off the
        // recorded `Take`. No power contributes at `ChallengePlacement`; a
        // first one that inserted a leaf between the payment and the note
        // would leave the note reading its step instead, so it must append
        // or the note must read the state as Travel's does.
        Note(ProcedureNotes.id(ActionRef.Challenge), tookNote(actor, banner,
          BannerRules.holder(ready.game.current, banner))),
        custody(ready, actor, banner)), Some(PowerWindow.ChallengePlacement))))
      .getOrElse(Vector[Operation](BuildOps((_, _) => Left(
        OathViolation.InvalidEventOrder(
          "Challenge reached its effects without a banner and an amount")))))

  /** What the challenger put on the banner, in the step before the note,
    * and who held it. Nothing paid writes nothing. */
  private def tookNote(actor: PlayerId, banner: Banner,
      holder: Option[PlayerId])(states: NoteStates): Option[LogNote] =
    states.previous.map(step => banner match
      case Banner.PeoplesFavor => -NoteSupport.favor(step, actor)
      case Banner.DarkestSecret => -NoteSupport.secrets(step, actor))
      .filter(_ > 0).map(paid => holder match
        case Some(rival) => fromPlayer(banner).sourceless(
          NoteArg.Banner(banner), NoteArg.Player(rival), NoteArg.Number(paid))
        case None => fromBank(banner).sourceless(
          NoteArg.Banner(banner), NoteArg.Number(paid)))

  private def fromPlayer(banner: Banner): NoteKey = banner match
    case Banner.PeoplesFavor => tookFromPlayerWithFavor
    case Banner.DarkestSecret => tookFromPlayerWithSecrets

  private def fromBank(banner: Banner): NoteKey = banner match
    case Banner.PeoplesFavor => tookFromBankWithFavor
    case Banner.DarkestSecret => tookFromBankWithSecrets

  private def payment(actor: PlayerId, banner: Banner, amount: Int): Operation =
    Move(banner match {
      case Banner.PeoplesFavor => Piece.Favor(amount)
      case Banner.DarkestSecret => Piece.Secrets(amount)
    }, PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.OnBanner(banner)))

  /** The banner moves to the challenger by a required `Take`: the payment
    * buys it, so a refused custody fails the path and the search hides that
    * banner. The walker checks the `Take` whole and records its `Move`, which
    * the Game Log reads.
    */
  private def custody(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation =
    Take(Piece.Banner(banner), actor,
      BannerRules.holder(ready.game.current, banner)
        .fold[Location](Location.SharedBank)(Location.PlayArea(_)),
      Location.PlayArea(actor), required = true)

  private def bannerOf(pending: PendingTree): Option[Banner] =
    pending.answered.collectFirst:
      case Answered(`bannerDecisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Banner(banner)), _) => banner

  private def amountOf(pending: PendingTree): Option[Int] =
    pending.answered.collectFirst:
      case Answered(`amountDecisionId`, DecisionAnswer.ChooseAmountAnswer(n), _) =>
        n
