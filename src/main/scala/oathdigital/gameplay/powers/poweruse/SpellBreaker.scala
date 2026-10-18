package oathdigital.gameplay.powers.poweruse

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction, PowerUseStep, Waiver}
import oathdigital.model._

object SpellBreakerCard extends Denizen(DenizenId("162"), "Spell Breaker", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.spell-breaker"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Spell Breaker's ruler **cannot** use powers that " +
      "cost any [secret] or [secret-burnt].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Spell Breaker (card 162, site-only): "Enemies of Spell Breaker's ruler
  * cannot use powers that cost any [secret] or [secret-burnt]."
  *
  * It refuses every use that costs a secret ([[PowerUse.costsSecret]]) to
  * the enemies of its site's ruler ([[RulerBans]]). A player ruler may
  * consent outside a Campaign; a grant covers the card's use for the turn.
  * It hooks no window and writes no line: a refused power is not used.
  */
case object SpellBreaker extends ContributingPower with PowerUseRestriction:
  val id: PowerId = SpellBreakerCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  private val notes = NoteSet(id)
  override def noteKeys: Vector[NoteKey] = notes.declared

  /** "Cannot use powers that cost secrets." */
  val plain: NoteKey = notes.key("no-secret-powers", Vector(NotePart.Text(
    "Cannot use powers that cost secrets.")))
  /** "Cannot use powers that cost secrets without ruler's consent." */
  val asked: NoteKey = notes.key("no-secret-powers-consent", Vector(
    NotePart.Text("Cannot use powers that cost secrets without ruler's " +
      "consent.")))

  /** "{card} stays deactivated." (automatic power consent design). */
  val staysDeactivated: NoteKey = notes.borrow(PowerUseStep.staysDeactivated)

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      ruler <- RulerBans.ruler(ready, SpellBreakerCard.id)
      if use.costsSecret && RulerBans.enemy(ruler, use.user)
    yield RulerBans.refusal(id, SpellBreakerCard.id, ruler, use.user,
      ConsentKey(id, use.user, PowerUseRules.useKey,
        PowerUseRules.target(use)), plain, asked, "spell-breaker")

  override val waiver: Option[Waiver] =
    Some(RulerBans.waiver(SpellBreakerCard.id, asked))
