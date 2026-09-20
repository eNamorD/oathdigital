package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Garrison (card 7), WHEN PLAYED: gain one warband per site you rule, and
  * put one warband from your board on each site you rule.
  *
  * Three siblings run in order. The gain is optional and capped by the bank.
  * The decision is a live `Branch` that exists only when the board holds
  * fewer warbands than there are ruled sites, read after the gain ran (the
  * walker re-selects it against the same stored state on resume). The
  * placement puts a warband on every ruled site, or on the chosen ones.
  */
final case class Garrison private (cardId: DenizenId) extends WhenPlayedPower {
  import Garrison._
  def id: PowerId = Garrison.id

  def effect(ctx: PowerCtx): Vector[Operation] = {
    val actor = ctx.activePlayer
    Vector(
      BuildOps((ready, _) => gain(ready, actor)),
      Branch((ready, _) => ask(ready, actor)),
      BuildOps((ready, pending) => place(ready, actor, pending)))
  }
}

object Garrison {
  val id: PowerId = PowerId("denizen.garrison")
  val decisionId: String = "cardplay.garrison.sites"

  def forCatalog(catalog: ExecutableCatalog): Option[Garrison] =
    WhenPlayedPower.cardOf(catalog, id).map(new Garrison(_))

  private def ruled(ready: ReadyGame, actor: PlayerId): Vector[SiteId] =
    PowerAccess.ruledSites(ready, actor).toVector.sortBy(_.value)

  private def gain(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val count = ruled(ready, actor).size
    if (count == 0) Right(Vector.empty)
    else PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, count)))
  }

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val sites = ruled(ready, actor)
    val held = PlayerFacts.player(ready, actor).map(_.board.warbands)
      .getOrElse(0)
    if (held == 0 || held >= sites.size) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseMany(held, held,
      sites.map(site => DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some("Garrison: choose the sites that each receive a warband"))))
  }

  private def place(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val sites = ruled(ready, actor)
    for {
      held <- PlayerFacts.player(ready, actor).map(_.board.warbands)
      kind <- PlayerFacts.forceKind(ready, actor)
      chosen <-
        if (held >= sites.size) Right(sites)
        else if (held == 0) Right(Vector.empty[SiteId])
        else answered(pending)
    } yield chosen.map(site => Move(Piece.Warbands(kind, 1),
      PositionedLocation(Location.PlayArea(actor)),
      PositionedLocation(Location.Site(site))))
  }

  private def answered(pending: PendingTree)
      : Either[OathViolation, Vector[SiteId]] =
    pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseManyAnswer(chosen), _) =>
        chosen.collect { case DecisionOptionRef.Site(site) => site }
    }.toRight(OathViolation.InvalidEventOrder(
      "no Garrison site choice is recorded"))
}
