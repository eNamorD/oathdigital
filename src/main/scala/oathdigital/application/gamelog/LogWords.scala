package oathdigital.application.gamelog

import oathdigital.application.{GamePresentationProjector,
  PreviewModifierDescriptions}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** A card as one viewer may read it: by name, or by its back's kind. */
private[gamelog] enum CardWord:
  case Named(span: LogSpan.Card)
  case Back(kind: String)

/** Every name the log writes, from the same presentation projector the board
  * uses, and the one visibility rule (spec, "Names" and "Visibility").
  */
private[gamelog] final class LogWords(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector):
  private val descriptions = new PreviewModifierDescriptions(catalog,
    presentation)

  def player(id: PlayerId): LogSpan =
    LogSpan.Player(id.value, presentation.playerLabel(id))
  def site(id: SiteId): LogSpan =
    LogSpan.Site(id.value, presentation.siteLabel(id))
  def banner(banner: Banner): LogSpan =
    LogSpan.Text(BannerRules.displayName(banner))
  /** A Vision a victory names: public by then. */
  def vision(id: VisionId): LogSpan =
    LogSpan.Card(id.value, presentation.cardDetails(id, None,
      hidden = false).name)

  /** Named when the viewer identifies the card where it lies before the
    * operation, or where it lies after it; otherwise its back. */
  def card(id: CardId, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): CardWord =
    if presentation.identifiesAt(before, viewer, id) ||
        presentation.identifiesAt(after, viewer, id) then
      CardWord.Named(LogSpan.Card(id.value, presentation.cardLabel(after, id)))
    else CardWord.Back(LogWords.backOf(id))

  /** Cards as one phrase: named cards in order, then backs counted by kind,
    * "Tinker, 2 Denizens and a Vision". */
  def cards(words: Vector[CardWord]): Vector[LogSpan] =
    val named = words.collect { case CardWord.Named(span) =>
      Vector[LogSpan](span) }
    val backs = words.collect { case CardWord.Back(kind) => kind }
    val counted = backs.distinct.map { kind =>
      val count = backs.count(_ == kind)
      Vector[LogSpan](LogSpan.Text(
        if count == 1 then s"a $kind" else s"$count ${LogWords.plural(kind)}"))
    }
    LogWords.join(named ++ counted)

  def one(word: CardWord): Vector[LogSpan] = cards(Vector(word))

  /** A power named by the card it is printed on, as the modifier picker
    * names it; the card follows the visibility rule on `ready`. */
  def power(ready: ReadyGame, actor: PlayerId, id: PowerId,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    descriptions.printedOn(ready, actor, id.value).fold(
      Vector[LogSpan](LogSpan.Text(presentation.safeLabel(id.value))))(card =>
      one(this.card(card, ready, ready, viewer)))

private[gamelog] object LogWords:
  def backOf(id: CardId): String = id match
    case _: VisionId => "Vision"
    case _: RelicId => "Relic"
    case _: EdificeId => "Edifice"
    case _: LegacyId => "Legacy"
    case _ => "Denizen"

  def plural(kind: String): String =
    if kind == "Legacy" then "Legacies" else s"${kind}s"

  /** "A", "A and B", "A, B and C". */
  def join(items: Vector[Vector[LogSpan]]): Vector[LogSpan] = items match
    case Vector() => Vector.empty
    case Vector(only) => only
    case _ =>
      val leading = items.init.zipWithIndex.flatMap { case (item, index) =>
        if index == 0 then item else LogSpan.Text(", ") +: item }
      leading ++ (LogSpan.Text(" and ") +: items.last)
