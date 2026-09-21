package oathdigital.gameplay.powers.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** The Truthful Harp (relic R04), a selected Search modifier: a Search draws 2
  * more cards, and every card drawn is revealed while it is in the player's
  * hand. The Harp and Augury stack.
  *
  * A card in a temporary hand is private to its owner and has no orientation,
  * so it cannot be turned faceup. It is revealed by recording that every other
  * player has seen it (`Peek`), which is what a public reveal amounts to in the
  * recorded state: the identity of a card that later becomes a facedown
  * adviser is then known to all. The reveal is a node of its own after the
  * draw, so it covers every card drawn whichever other extension ran.
  */
final case class TruthfulHarp private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = TruthfulHarp.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((ctx, operations) =>
      DrawExtension.extend(operations, TruthfulHarp.More) :+
        reveal(ctx.activePlayer))))

  private def reveal(actor: PlayerId): Operation = BuildOps((ready, _) => Right(
    for {
      viewer <- ready.game.current.players.map(_.player).filter(_ != actor)
      card <- ready.game.current.temporaryHands.getOrElse(actor, Vector.empty)
    } yield Peek(viewer, card, Location.Hand(actor))))
}

object TruthfulHarp {
  val id: PowerId = PowerId("relic.truthful-harp")
  val More: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[TruthfulHarp] =
    CatalogCards.relic(catalog, id).map(new TruthfulHarp(_, catalog))
}
