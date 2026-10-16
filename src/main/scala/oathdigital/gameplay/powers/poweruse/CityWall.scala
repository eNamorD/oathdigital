package oathdigital.gameplay.powers.poweruse

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction, Waiver}
import oathdigital.model._

object CityWallCard extends Denizen(DenizenId("252"), "City Wall", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.city-wall"),
    persistent = true, cost = Cost.free,
    text = "While at this site, enemies of City Wall's ruler **cannot** " +
      "place [favor] / [secret] on cards or use non-persistent " +
      "powers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** City Wall (card 252, site-only): "While at this site, enemies of City
  * Wall's ruler cannot place [favor] / [secret] on cards or use
  * non-persistent powers."
  *
  * An enemy of its site's ruler ([[RulerBans]]) whose pawn stands at that
  * site cannot use a non-persistent power from any card, a use whose cost
  * places on its card ([[Placements.byUse]]), or place on any card
  * ([[Placements.onto]]). The pawn is the physical one: an as-if site does
  * not count. A player ruler may consent outside a Campaign. It writes no
  * line.
  */
case object CityWall extends ContributingPower with PowerUseRestriction:
  val id: PowerId = CityWallCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  private val notes = NoteSet(id)
  override def noteKeys: Vector[NoteKey] = notes.declared
  /** "Cannot use non-persistent powers while at its site." */
  val usePlain: NoteKey = notes.key("no-powers-here", Vector(NotePart.Text(
    "Cannot use non-persistent powers while at its site.")))
  /** "... without ruler's consent." */
  val useAsked: NoteKey = notes.key("no-powers-here-consent", Vector(
    NotePart.Text("Cannot use non-persistent powers while at its site " +
      "without ruler's consent.")))
  /** "Cannot place favor or secrets on cards while at its site." */
  val placingPlain: NoteKey = notes.key("no-placing-here", Vector(
    NotePart.Text("Cannot place favor or secrets on cards while at its " +
      "site.")))
  /** "... without ruler's consent." */
  val placingAsked: NoteKey = notes.key("no-placing-here-consent", Vector(
    NotePart.Text("Cannot place favor or secrets on cards while at its " +
      "site without ruler's consent.")))

  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)
  override val waiver: Option[Waiver] =
    Some(RulerBans.waiver(CityWallCard.id, useAsked))

  /** City Wall's ruler, when `player` is their enemy and stands at its
    * site. */
  private def walledIn(ready: ReadyGame, player: PlayerId)
      : Option[SiteRuler] =
    for
      site <- SiteRulers.siteOf(ready, CityWallCard.id)
      if PowerAccess.pawnSite(ready, player).contains(site)
      ruler <- RulerBans.ruler(ready, CityWallCard.id)
      if RulerBans.enemy(ruler, player)
    yield ruler

  /** The plain and asked reasons for `use`: the use's own when it is
    * non-persistent, the placing ones when its cost places now. */
  private def reasons(ready: ReadyGame, use: PowerUse)
      : Option[(NoteKey, NoteKey)] =
    if !use.persistent then Some(usePlain -> useAsked)
    else Placements.byUse(ready, use).map(_ => placingPlain -> placingAsked)

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      ruler <- walledIn(ready, use.user)
      keys <- reasons(ready, use)
    yield RulerBans.refusal(id, CityWallCard.id, ruler, use.user,
      ConsentKey(id, use.user, PowerUseRules.useKey,
        PowerUseRules.target(use)), keys._1, keys._2, "city-wall")

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] =
      Placements.onto(ready, operation).flatMap(placed =>
        walledIn(ready, placed._1).map(ruler => RulerBans.reason(id,
          CityWallCard.id, ruler, placed._1, ConsentKey(id, placed._1,
            PowerUseRules.useKey, PowerUseRules.cardRef(placed._2)),
          placingAsked, "city-wall-placing")))
