package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object GolemLegionsCard extends Denizen(DenizenId("199"), "Golem Legions", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.golem-legions"),
    persistent = false, cost = Cost.free,
    text = "You may muster by placing [favor] on Golem Legions. _It " +
      "counts itself as a matching adviser._")
  val powers: Vector[PrintedPower] = Vector(power)

/** Golem Legions (card 199, adviser-only), a selected Muster modifier with
  * no cost: its holder may Muster on Golem Legions itself, which takes the
  * Muster's favor as a card at a site would.
  *
  * It adds Golem Legions to the source decision, after the cards at the pawn
  * site ([[EconomySources.widened]]). The token-free rule still applies: while
  * Golem Legions holds favor or secrets it is shown unavailable, so it is
  * mustered on once between Rests unless Pressgangs is also selected.
  *
  * "It counts itself as a matching adviser" is a reminder. A selectable
  * Golem Legions is a faceup Arcane adviser, which `MusterSource.matching`
  * already counts. It writes no line: the start line names it, and the
  * Muster's own line names it as the source.
  */
final case class GolemLegions private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = GolemLegionsCard.id
  def id: PowerId = GolemLegions.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.widened(ctx, operations,
        held(ctx.state, ctx.activePlayer)))))

  /** Golem Legions among `actor`'s advisers. */
  private def held(ready: ReadyGame, actor: PlayerId)
      : Vector[SiteDenizenState] =
    ready.game.current.players.find(_.player == actor).toVector
      .flatMap(_.advisers).collect {
        case card: DenizenState if card.id == cardId => card
      }

object GolemLegions:
  val id: PowerId = GolemLegionsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): GolemLegions =
    new GolemLegions(catalog)
