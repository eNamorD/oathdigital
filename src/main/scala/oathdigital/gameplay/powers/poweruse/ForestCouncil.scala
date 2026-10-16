package oathdigital.gameplay.powers.poweruse

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction, Waiver}
import oathdigital.model._

object ForestCouncilCard extends Denizen(DenizenId("194"), "Forest Council", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forest-council"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Forest Council's ruler **cannot** place [favor] " +
      "/ [secret] on [suit-beast] cards or use their " +
      "non-persistent powers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Forest Council (card 194, site-only): "Enemies of Forest Council's
  * ruler cannot place [favor] / [secret] on [suit-beast] cards or use their
  * non-persistent powers."
  *
  * A Beast card is one whose effective suit is Beast (`EffectiveSuit.of`):
  * its printed suit, unless it is an adviser pledged to Discord. The enemies are its site's ruler's
  * ([[RulerBans]]). It refuses an enemy's non-persistent use of a Beast
  * card, a use whose cost places on one ([[Placements.byUse]]), and a
  * placement on one ([[Placements.onto]]). A player ruler may consent
  * outside a Campaign. One grant, under the card's use key, covers both.
  * It writes no line.
  */
final case class ForestCouncil private (catalog: ExecutableCatalog)
    extends ContributingPower with PowerUseRestriction:
  import ForestCouncil._

  def id: PowerId = ForestCouncil.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  override def noteKeys: Vector[NoteKey] = ForestCouncil.noteKeys
  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)
  override def waiver: Option[Waiver] =
    Some(RulerBans.waiver(ForestCouncilCard.id, useAsked))

  private def beast(ready: ReadyGame, card: CardId): Boolean =
    EffectiveSuit.of(catalog, ready, card).contains(Suit.Beast)

  private def covers(player: PlayerId, card: CardId): ConsentKey =
    ConsentKey(id, player, PowerUseRules.useKey, PowerUseRules.cardRef(card))

  /** The plain and asked reasons for `use`: the use's own when it is
    * non-persistent, the placing ones when its cost places now. */
  private def reasons(ready: ReadyGame, use: PowerUse)
      : Option[(NoteKey, NoteKey)] =
    if !use.persistent then Some(usePlain -> useAsked)
    else Placements.byUse(ready, use).map(_ => placingPlain -> placingAsked)

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      ruler <- RulerBans.ruler(ready, ForestCouncilCard.id)
      if RulerBans.enemy(ruler, use.user)
      card <- use.card
      if beast(ready, card)
      keys <- reasons(ready, use)
    yield RulerBans.refusal(id, ForestCouncilCard.id, ruler, use.user,
      covers(use.user, card), keys._1, keys._2, "forest-council")

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] =
      for
        placed <- Placements.onto(ready, operation)
        if beast(ready, placed._2)
        ruler <- RulerBans.ruler(ready, ForestCouncilCard.id)
        if RulerBans.enemy(ruler, placed._1)
      yield RulerBans.reason(id, ForestCouncilCard.id, ruler, placed._1,
        covers(placed._1, placed._2), placingPlain, placingAsked,
        "forest-council-placing")

object ForestCouncil:
  val id: PowerId = ForestCouncilCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Cannot use non-persistent powers of Beast cards." */
  val usePlain: NoteKey = notes.key("no-beast-powers", Vector(NotePart.Text(
    "Cannot use non-persistent powers of Beast cards.")))
  /** "... without ruler's consent." */
  val useAsked: NoteKey = notes.key("no-beast-powers-consent", Vector(
    NotePart.Text("Cannot use non-persistent powers of Beast cards " +
      "without ruler's consent.")))
  /** "Cannot place favor or secrets on Beast cards." */
  val placingPlain: NoteKey = notes.key("no-beast-placing", Vector(
    NotePart.Text("Cannot place favor or secrets on Beast cards.")))
  /** "... without ruler's consent." */
  val placingAsked: NoteKey = notes.key("no-beast-placing-consent", Vector(
    NotePart.Text("Cannot place favor or secrets on Beast cards without " +
      "ruler's consent.")))

  def forCatalog(catalog: ExecutableCatalog): ForestCouncil =
    new ForestCouncil(catalog)
