package oathdigital.gameplay.actions.challenge

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** Challenge on the walker: spend one Supply, choose a banner, choose how many
  * resources replace it, return the banner's resources (the ribbon), then pay
  * the replacement and take the banner. Takes no start selection.
  *
  * The Supply is spent first so no decision reads it. The banner and the
  * amount are decided before any resource moves, so both read the board as
  * the player sees it; the ribbon and the payment are built when reached.
  */
object ChallengeProcedure {
  val bannerDecisionId: String = "challenge.banner"
  val amountDecisionId: String = "challenge.amount"
  val decisionIds: Set[String] =
    Set(bannerDecisionId, amountDecisionId, ChallengeRibbon.siteDecisionId)

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- supportedFaces(state)
    _ <- Either.cond(legalBanners(state, actor).nonEmpty, (),
      OathViolation.NoPlayableOption(ActionRef.Challenge.key))
  } yield tree(actor)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(actor))

  /** The banners the actor could challenge now, in the banners' declared
    * order: not already held by the actor, the holder (if any) at the actor's
    * site, and strictly more relevant faceup resources than the banner holds.
    * Supply is a cost owned by `SpendSupply`, not a fact about a banner.
    */
  def legalBanners(state: ReadyGame, actor: PlayerId): Vector[Banner] = {
    val current = state.game.current
    current.players.find(_.player == actor).toVector.flatMap { player =>
      Banner.all.filter { banner =>
        val holder = BannerRules.holder(current, banner)
        !holder.contains(actor) &&
          holder.forall(rival => player.pawnSite.nonEmpty && player.pawnSite ==
            current.players.find(_.player == rival).flatMap(_.pawnSite)) &&
          BannerRules.playerResources(player, banner) >
            BannerRules.resources(current, banner)
      }
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
  private def supportedFaces(state: ReadyGame): Either[OathViolation, Unit] = {
    val banners = state.game.current.banners
    Either.cond(banners.peoplesFavor.active == PeoplesFavorFace.Mob &&
      banners.darkestSecret.active == DarkestSecretFace.WanderingFlame, (),
      OathViolation.UnsupportedBannerState("unsupported active banner face"))
  }

  private def tree(actor: PlayerId): Operation = Sequence(Vector[Operation](
    Sequence(Vector[Operation](SpendSupply(actor, 1)),
      Some(PowerWindow.ChallengeCost)),
    Branch((ready, _) => Vector(Decide(bannerDecisionId, actor,
      DecisionQuery.ChooseOne(legalBanners(ready, actor).map(banner =>
        DecisionOption.Banner(DecisionOptionRef.Banner(banner))),
        heading = Some("Choose a banner to Challenge")),
      window = Some(PowerWindow.ChallengeBannerSelection)))),
    Branch((ready, pending) => bannerOf(pending).toVector.map(banner =>
      amountDecision(ready, actor, banner))),
    Branch((ready, pending) => effects(ready, actor, pending))),
    Some(PowerWindow.ChallengeActionEligibility))

  private def amountDecision(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation = {
    val current = ready.game.current
    val prior = BannerRules.resources(current, banner)
    val own = current.players.find(_.player == actor)
      .fold(0)(BannerRules.playerResources(_, banner))
    val unit = banner match {
      case Banner.PeoplesFavor => "favor"
      case Banner.DarkestSecret => "secrets"
    }
    Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(prior + 1,
      math.max(prior + 1, own),
      Some(s"Place more than $prior $unit to take ${banner.key}"),
      "Take banner"),
      window = Some(PowerWindow.ChallengeAmountSelection))
  }

  /** Reached only after both answers exist. Selecting reads the two answers
    * and the banner's holder, which no earlier step changes; the ribbon and
    * the payment amount are built when walked.
    */
  private def effects(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    (for {
      banner <- bannerOf(pending)
      amount <- amountOf(pending)
    } yield Vector[Operation](
      Sequence(ChallengeRibbon.steps(actor, banner),
        Some(PowerWindow.ChallengeRibbon)),
      Sequence(Vector[Operation](payment(actor, banner, amount),
        custody(ready, actor, banner)), Some(PowerWindow.ChallengePlacement))))
      .getOrElse(Vector[Operation](BuildOps((_, _) => Left(
        OathViolation.InvalidEventOrder(
          "Challenge reached its effects without a banner and an amount")))))

  private def payment(actor: PlayerId, banner: Banner, amount: Int): Operation =
    Move(banner match {
      case Banner.PeoplesFavor => Piece.Favor(amount)
      case Banner.DarkestSecret => Piece.Secrets(amount)
    }, PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.OnBanner(banner)))

  private def custody(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation =
    Move(Piece.Banner(banner),
      BannerRules.holder(ready.game.current, banner).fold(
        PositionedLocation(Location.SharedBank))(holder =>
        PositionedLocation(Location.PlayArea(holder))),
      PositionedLocation(Location.PlayArea(actor)))

  private def bannerOf(pending: PendingTree): Option[Banner] =
    pending.answered.collectFirst {
      case Answered(`bannerDecisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Banner(banner)), _) => banner
    }

  private def amountOf(pending: PendingTree): Option[Int] =
    pending.answered.collectFirst {
      case Answered(`amountDecisionId`, DecisionAnswer.ChooseAmountAnswer(n), _) =>
        n
    }
}
