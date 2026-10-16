package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission,
  SiteRemoval}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object ForestWardenCard extends Denizen(DenizenId("218"), "Forest Warden",
    Suit.Beast):
  val power = PrintedPower(PowerId("denizen.forest-warden"),
    persistent = false, cost = Cost.free,
    text = "If you play a [suit-beast] card to a site, you may bury a " +
      "card at the site first.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Forest Warden (card 218), a selected Search modifier with no cost: "If
  * you play a [suit-beast] card to a site, you may bury a card at the site
  * first." (catalog batch 8, slice 12).
  *
  * Through the `SearchPlayAdviser` window it adds one bury permission when
  * the played card's effective suit is Beast. Card play then offers each
  * card of the site's row as "Bury and replace" (a Replace the player is
  * asked about when a discard covers it too), each relic there as "Bury
  * and play" while the site has room, and a bury follow-up after a
  * Replace that discarded. Bury ignores locks.
  *
  * Its line, "May bury a card at {site} first.", travels in the
  * permission, and card play writes it for the bury it allowed.
  */
final case class ForestWarden private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = ForestWardenCard.id
  def id: PowerId = ForestWarden.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  private val notes = NoteSet(id)
  notes.include(CardPlayRules.notes)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match
        case tree: CardPlayProcedure.PlacementTree
            if EffectiveSuit.of(catalog, ctx.state, tree.card)
              .contains(Suit.Beast) =>
          tree.adjust(children)(_.permitSiteDiscard(DiscardPermission.lined(
            id, PowerSourceRef.Card(cardId), SiteRemoval.Bury)))
        case _ => children)))

object ForestWarden:
  val id: PowerId = ForestWardenCard.power.id

  def forCatalog(catalog: ExecutableCatalog): ForestWarden =
    new ForestWarden(catalog)
