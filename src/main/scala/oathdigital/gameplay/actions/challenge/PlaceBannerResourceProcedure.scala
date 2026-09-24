package oathdigital.gameplay.actions.challenge

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.model._

/** Place Banner Resource on the walker: the holder of a banner moves favor or
  * faceup secrets from their own board onto it, for no Supply. Takes no start
  * selection; the banner and the amount are decisions.
  */
object PlaceBannerResourceProcedure {
  val bannerDecisionId: String = "place-banner-resource.banner"
  val amountDecisionId: String = "place-banner-resource.amount"
  val decisionIds: Set[String] = Set(bannerDecisionId, amountDecisionId)

  /** The amount question's own copy: the banner as it is printed, and the
    * one resource that banner takes.
    */
  def amountHeading(banner: Banner): String =
    s"Place ${BannerRules.resourceName(banner)} on " +
      BannerRules.displayName(banner)

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- Either.cond(heldBanners(state, actor).nonEmpty, (),
      OathViolation.NoPlayableOption(ActionRef.PlaceBannerResource.key))
  } yield tree(actor)

  /** Whether Place Banner Resource could start now. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .exists(WalkerSimulation.starts(_, state, powers))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(actor))

  /** Banners the actor holds and could put at least one resource on. */
  def heldBanners(state: ReadyGame, actor: PlayerId): Vector[Banner] = {
    val current = state.game.current
    current.players.find(_.player == actor).toVector.flatMap(player =>
      Banner.all.filter(banner => BannerRules.holder(current, banner)
        .contains(actor) && BannerRules.playerResources(player, banner) > 0))
  }

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.PlaceBannerResource.key} takes no start " +
        s"selection, got ${args.map(_.kind).mkString(", ")}"))

  private def tree(actor: PlayerId): Operation = Sequence(Vector[Operation](
    Branch((ready, _) => Vector(Decide(bannerDecisionId, actor,
      DecisionQuery.ChooseOne(heldBanners(ready, actor).map(banner =>
        DecisionOption.Banner(DecisionOptionRef.Banner(banner))),
        heading = Some("Choose a banner to place resources on")),
      window = Some(PowerWindow.PlaceBannerResourceBannerSelection)))),
    Branch((ready, pending) => bannerOf(pending).toVector.map(banner =>
      amountDecision(ready, actor, banner))),
    Branch((_, pending) => placement(actor, pending))),
    Some(PowerWindow.PlaceBannerResourceEligibility))

  private def placement(actor: PlayerId, pending: PendingTree): Vector[Operation] =
    (for {
      banner <- bannerOf(pending)
      amount <- amountOf(pending)
    } yield Vector[Operation](Sequence(Vector[Operation](Move(banner match {
      case Banner.PeoplesFavor => Piece.Favor(amount)
      case Banner.DarkestSecret => Piece.Secrets(amount)
    }, PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.OnBanner(banner)))),
      Some(PowerWindow.PlaceBannerResourcePlacement)))).getOrElse(
      Vector[Operation](BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(
        "Place Banner Resource reached its move without a banner and an amount")))))

  private def amountDecision(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation = {
    val own = ready.game.current.players.find(_.player == actor)
      .fold(0)(BannerRules.playerResources(_, banner))
    Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(1,
      math.max(1, own), Some(amountHeading(banner)), "Place resources"),
      window = Some(PowerWindow.PlaceBannerResourceAmountSelection))
  }

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
