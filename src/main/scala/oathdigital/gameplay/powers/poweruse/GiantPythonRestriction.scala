package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction}
import oathdigital.gameplay.powers.action.GiantPythonCard
import oathdigital.model._

/** Giant Python's restriction (card 186, site-only): "Other cards at this
  * site cannot be used for non-persistent powers or placing [favor] /
  * [secret]."
  *
  * The other cards are the denizens and edifices at the site where Giant
  * Python stands now, so the restriction moves with it. It refuses a
  * non-persistent use of one, a use whose cost places on one
  * ([[Placements.byUse]]), and a placement on one ([[Placements.onto]]).
  * It binds every player, its ruler included. It names no enemies, so no
  * consent lifts it (consent design, ruling 1). It writes no line.
  */
case object GiantPythonRestriction extends ContributingPower
    with PowerUseRestriction:
  val id: PowerId = GiantPythonCard.restriction.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  private val notes = NoteSet(id)
  override def noteKeys: Vector[NoteKey] = notes.declared
  /** "Cards at its site cannot be used for non-persistent powers." */
  val usePlain: NoteKey = notes.key("no-powers-here", Vector(NotePart.Text(
    "Cards at its site cannot be used for non-persistent powers.")))
  /** "Cannot place favor or secrets on cards at its site." */
  val placingPlain: NoteKey = notes.key("no-placing-here", Vector(
    NotePart.Text("Cannot place favor or secrets on cards at its site.")))

  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)

  private val cardSource = PowerSourceRef.Card(GiantPythonCard.id)

  /** Whether `card` is another card at Giant Python's site. */
  private def guarded(ready: ReadyGame, card: CardId): Boolean =
    card != GiantPythonCard.id && SiteRulers.siteOf(ready,
      GiantPythonCard.id).exists(site => ready.game.current.map.sites
      .get(site).exists(_.denizens.exists(_.id == card)))

  private def refused(code: String, player: PlayerId): OperationReason =
    OperationReason(code, s"cards at Giant Python's site cannot be used " +
      s"by ${player.value}", OperationReasonKind.Impossible)

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      card <- use.card
      if guarded(ready, card)
      key <- if !use.persistent then Some(usePlain)
        else Placements.byUse(ready, use).map(_ => placingPlain)
    yield PowerUseRefusal(id, key(cardSource),
      refused("giant-python", use.user))

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] =
      Placements.onto(ready, operation).filter(placed =>
        guarded(ready, placed._2)).map(placed =>
        refused("giant-python-placing", placed._1))
