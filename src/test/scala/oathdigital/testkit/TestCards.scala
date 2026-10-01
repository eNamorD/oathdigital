package oathdigital.testkit

import oathdigital.catalog._
import oathdigital.model.{Cost, DenizenId, EdificeId, PowerId, RelicId,
  SiteId, Suit, Tokens}

/** Cards for suites that graft a power or a name onto a catalog card, or
  * build a small catalog of their own. Card objects have no `copy`, so a
  * `…Like` variant rebuilds the card and keeps its printed restriction traits.
  */
object TestCards:
  def power(id: String, text: String = "Test power."): PrintedPower =
    PrintedPower(PowerId(id), persistent = false, Cost.free, text)

  /** True for a denizen that prints no restriction. */
  def unrestricted(card: Denizen): Boolean = card match
    case _: Locked | _: SiteOnly | _: AdviserOnly => false
    case _ => true

  def denizen(id: DenizenId, name: String, suit: Suit,
      powers: Vector[PrintedPower]): Denizen =
    val printed = powers
    new Denizen(id, name, suit) { val powers = printed }

  /** `base` with another name or other powers, keeping its restrictions. */
  def denizenLike(base: Denizen)(name: String = base.name,
      powers: Vector[PrintedPower] = base.powers): Denizen =
    val printed = powers
    base match
      case _: (Locked & AdviserOnly) =>
        new Denizen(base.id, name, base.suit) with Locked with AdviserOnly {
          val powers = printed }
      case _: AdviserOnly =>
        new Denizen(base.id, name, base.suit) with AdviserOnly {
          val powers = printed }
      case _: SiteOnly =>
        new Denizen(base.id, name, base.suit) with SiteOnly {
          val powers = printed }
      case _ =>
        new Denizen(base.id, name, base.suit) { val powers = printed }

  def relic(id: RelicId, name: String, powers: Vector[PrintedPower]): Relic =
    val printed = powers
    new Relic(id, name, value = 1, defense = 0) { val powers = printed }

  def edifice(id: EdificeId, suit: Suit, intactName: String,
      intactPowers: Vector[PrintedPower], ruinedName: String,
      ruinedPowers: Vector[PrintedPower]): Edifice =
    new Edifice(id, suit) {
      val intact: EdificeFace = new EdificeFace(intactName) with Locked {
        val powers = intactPowers }
      val ruined: EdificeFace = new EdificeFace(ruinedName) {
        val powers = ruinedPowers }
    }

  /** `base` with other powers on either face. */
  def edificeLike(base: Edifice)(
      intact: Vector[PrintedPower] = base.intact.powers,
      ruined: Vector[PrintedPower] = base.ruined.powers): Edifice =
    edifice(base.id, base.suit, base.intact.name, intact, base.ruined.name,
      ruined)

  /** `base` with another id, printed Forge cost or handlers. */
  def siteLike(base: Site)(id: SiteId = base.id,
      forgeRequirements: Option[Tokens] = base.forgeRequirements,
      handlers: Vector[String] = base.handlers): Site =
    new Site(id, base.name, base.defense, base.capacity, base.relicSlots,
      base.recoverDifficulty, base.startingResources, forgeRequirements,
      base.homeland, handlers) {}
