package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Crystal Vial (relic R21), ACTION: place 1 secret on this relic and burn
  * 1, then bury an adviser of yours (a denizen or a Vision, in either
  * orientation) or a card in the card list at your pawn's site (a denizen or
  * the edifice, in either state). The burial uses the standard returns:
  * favor to the card's suit bank, secrets to the acting player facedown.
  *
  * The choice is required when a candidate exists. It is a live `Branch`,
  * read after the cost is paid, and the effect recomputes the candidates
  * from live state. The power holds the catalog because `Bury.standard`
  * needs the card's suit to return favor.
  */
final case class CrystalVial(catalog: ExecutableCatalog)
    extends PaidAction(CrystalVial.id.value, CrystalVial.price) {
  import CrystalVial._

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending)))))

  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] = {
    val current = ready.game.current
    val advisers = current.players.find(_.player == actor).toVector
      .flatMap(_.advisers.map {
        case d: DenizenState => Candidate(DecisionOptionRef.Denizen(d.id),
          BuryableCard.Denizen(d.id), Location.PlayArea(actor), d.tokens)
        case v: VisionState => Candidate(DecisionOptionRef.Vision(v.id),
          BuryableCard.Vision(v.id), Location.PlayArea(actor), Tokens.empty)
      })
    val here = PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      current.map.sites.get(site).toVector.flatMap(_.denizens.map {
        case d: DenizenState => Candidate(DecisionOptionRef.Denizen(d.id),
          BuryableCard.Denizen(d.id), Location.Site(site), d.tokens)
        case e: EdificeState => Candidate(DecisionOptionRef.Edifice(e.id),
          BuryableCard.Edifice(e.id), Location.Site(site), e.tokens)
      }))
    advisers ++ here
  }

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] = {
    val found = candidates(ready, actor)
    if (found.isEmpty) Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.flatMap(c => DecisionOption.forRef(c.ref)), heading = Some(
        "Crystal Vial: bury an adviser of yours or a card at your site"))))
  }

  private def bury(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val found = candidates(ready, actor)
    if (found.isEmpty) Right(Vector.empty)
    else for {
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      chosen <- found.find(_.ref == ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not a card the Vial can bury"))
      suit = catalog.suitOf(chosen.card.id)
      _ <- Either.cond(chosen.tokens.favor == 0 || suit.isDefined, (),
        OathViolation.InvalidEventOrder(
          s"no suit is known for ${chosen.card.id.value}"))
    } yield Bury.standard(chosen.card, PositionedLocation(chosen.from), suit,
      chosen.tokens.favor, chosen.tokens.secrets, actor)
  }
}

object CrystalVial {
  val id: PowerId = PowerId("relic.crystal-vial")
  val price: Cost = Cost(secret = 1, secretBurnt = 1)
  val decisionId: String = "power.crystal-vial.card"

  private[action] final case class Candidate(ref: DecisionOptionRef,
      card: BuryableCard, from: Location, tokens: Tokens)
}
