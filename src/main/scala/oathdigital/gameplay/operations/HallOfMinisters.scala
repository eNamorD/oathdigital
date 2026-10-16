package oathdigital.gameplay.operations

import oathdigital.catalog.{Edifice, EdificeFace, Locked, PrintedPower}
import oathdigital.model._

object HallOfMinistersCard extends Edifice(EdificeId("E16"), Suit.Order):
  object intact extends EdificeFace("Hall of Ministers") with Locked:
    val power = PrintedPower(PowerId("edifice.e16.intact"),
      persistent = true, cost = Cost.free,
      text = "Enemies of this card's ruler **cannot** discard cards from " +
        "sites ruled by this card's ruler. _They can still be " +
        "buried._")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Hall of Bandits"):
    val power = PrintedPower(PowerId("edifice.e16.ruined"),
      persistent = true, cost = Cost.free,
      text = "**CHRONICLE:** Ruined edifices ruled by bandits are not " +
        "discarded.")
    val powers: Vector[PrintedPower] = Vector(power)

/** The Hall of Ministers (edifice E16, intact): an enemy of the Hall's ruler
  * acts as if the denizens and relics at the ruler's sites were locked, so it
  * cannot discard them. The actor is the discard's own acting player; a
  * Vision's discard names none, so it is the active player's. An actorless
  * discard, as Empty Grounds', has no actor, so no enemy, and goes through.
  *
  * Its ruler may consent (consent design, slice 3). The refusal names the
  * ruler and the card, and a grant covers that card's discard by that player
  * for the turn, on whichever path discards it.
  *
  * There is one Hall of Ministers, so the restriction names its card and
  * holds whichever edifices a catalog lists.
  */
case object HallOfMinisters extends OperationRestriction with WaivableRule:
  val id: PowerId = HallOfMinistersCard.intact.power.id
  val source: PowerSourceRef = PowerSourceRef.Card(HallOfMinistersCard.id)

  /** The decision slot of a Hall grant's key. The grant covers a card's
    * discard, which the operation check sees without its decision, so the
    * key names the discard instead. */
  val discardKey: String = "operation.discard"

  /** The keys this rule owns. */
  val notes: NoteSet = NoteSet.unowned("HallOfMinisters")
  /** "Cannot discard cards from its ruler's sites.": the reason of an
    * option nobody may be asked about. */
  val noDiscard: NoteKey = notes.key("no-discard", Vector(NotePart.Text(
    "Cannot discard cards from its ruler's sites.")))
  /** "Cannot discard cards from its ruler's sites without ruler's
    * consent.": the reason of an option the Hall's ruler may be asked
    * about. */
  val noDiscardConsent: NoteKey = notes.key("no-discard-consent", Vector(
    NotePart.Text("Cannot discard cards from its ruler's sites without " +
      "ruler's consent.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  def consentNote: NoteKey = noDiscardConsent

  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    discardAt(ready, operation).flatMap { case (site, actor, card) =>
      val current = ready.game.current
      val actorSide = current.players.find(_.player == actor).flatMap { player =>
        ready.game.campaign.lineages.get(player.lineage).map { lineage =>
          if lineage.role.isImperial then SiteRuler.Empire
          else SiteRuler.Player(actor)
        }
      }
      for
        sourceSide <- actorSide
        ruler <- siteRuler(ready, site)
        if SiteRule.enemies(sourceSide, ruler)
        if hallRuler(ready).contains(ruler)
      yield OperationReason("discard-immune",
        s"cards at site ${site.value} cannot be discarded by ${actor.value}",
        OperationReasonKind.Impossible, Some(OperationWaiver(id,
          granter(ready), ConsentKey(id, actor, discardKey, card),
          noDiscardConsent(source), noDiscard(source))))
    }

  /** The player who rules the intact Hall, who may consent; `None` when
    * bandits, the Empire or nobody rules it. */
  def granter(ready: ReadyGame): Option[PlayerId] =
    hallRuler(ready).collect { case SiteRuler.Player(player) => player }

  /** The ruler of the site where the intact Hall stands. */
  private def hallRuler(ready: ReadyGame): Option[SiteRuler] =
    ready.game.current.map.sites.collectFirst {
      case (site, state) if state.denizens.exists(intactHall) => site
    }.flatMap(siteRuler(ready, _))

  private def siteRuler(ready: ReadyGame, site: SiteId): Option[SiteRuler] =
    val current = ready.game.current
    current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, current.players).toOption)

  private def intactHall(card: SiteDenizenState): Boolean = card match
    case edifice: EdificeState =>
      edifice.side == EdificeSide.Intact &&
        edifice.id == HallOfMinistersCard.id
    case _ => false

  /** The site a discard takes a card from, who discards it, and the card. */
  private def discardAt(ready: ReadyGame, operation: CoreOperation)
      : Option[(SiteId, PlayerId, DecisionOptionRef)] =
    val active = ready.game.current.turn.activePlayer
    val source = operation match
      case value: Discard.Denizen => value.by.map(actor => (
        value.from.location, actor, DecisionOptionRef.Denizen(value.card)))
      case value: Discard.Vision => Some((value.from.location, active,
        DecisionOptionRef.Vision(value.card)))
      case value: Discard.RuinedEdifice => value.by.map(actor => (
        value.from.location, actor, DecisionOptionRef.Edifice(value.card)))
      case value: Discard.Relic => Some((value.from.location,
        value.actingPlayer, DecisionOptionRef.Relic(value.card)))
      case _ => None
    source.collect { case (Location.Site(site), actor, card) =>
      (site, actor, card) }
