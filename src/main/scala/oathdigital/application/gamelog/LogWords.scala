package oathdigital.application.gamelog

import oathdigital.application.GamePresentationProjector
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._
import oathdigital.protocol.projection.{DieFace => RolledFace, LogSpan}

/** A card as one viewer may read it: by name, or by its back's kind. */
private[gamelog] enum CardWord:
  case Named(span: LogSpan.Card)
  case Back(kind: String)

/** Every name the log writes, from the same presentation projector the board
  * uses, and the one visibility rule (spec, "Names" and "Visibility").
  */
private[gamelog] final class LogWords(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector):
  private val printed = new PrintedCards(catalog)

  /** "Verb " when `player` is the run's actor, else "Blue verb ". */
  def subject(player: PlayerId, actor: PlayerId, verb: String)
      : Vector[LogSpan] =
    if player == actor then Vector(LogSpan.Text(s"${verb.capitalize} "))
    else Vector(this.player(player), LogSpan.Text(s" $verb "))

  def label(key: String): String = presentation.safeLabel(key)

  def player(id: PlayerId): LogSpan =
    LogSpan.Player(id.value, presentation.playerLabel(id))
  def site(id: SiteId): LogSpan =
    LogSpan.Site(id.value, presentation.siteLabel(id))
  def banner(banner: Banner): LogSpan =
    LogSpan.Text(BannerRules.displayName(banner))
  /** The card, banner or site a power belongs to, judged at `states`. */
  def source(ref: PowerSourceRef, states: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = ref match
    case PowerSourceRef.Card(id) => one(seen(id, states, viewer))
    case PowerSourceRef.Banner(held) => Vector(banner(held))
    case PowerSourceRef.Site(at) => Vector(site(at))
  /** A Vision a victory names: public by then. */
  def vision(id: VisionId): LogSpan =
    LogSpan.Card(id.value, presentation.cardDetails(id, None,
      hidden = false).name)

  /** Named when the viewer identifies the card where it lies before the
    * operation, or where it lies after it; otherwise its back. */
  def card(id: CardId, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): CardWord = seen(id, Vector(before, after), viewer)

  /** Named, by its label in the last of `states`, when the viewer identifies
    * the card in any of them; otherwise its back. */
  def seen(id: CardId, states: Vector[ReadyGame],
      viewer: Option[PlayerId]): CardWord =
    states.lastOption.filter(_ =>
      states.exists(presentation.identifiesAt(_, viewer, id))) match
      case Some(last) =>
        CardWord.Named(LogSpan.Card(id.value, presentation.cardLabel(last, id)))
      case None => CardWord.Back(LogWords.backOf(id))

  /** A card in `owner`'s adviser or relic row: named when the viewer may
    * identify it, else by its 1-based slot in that row before the operation
    * (spec, "Negotiation"). */
  def slotted(id: CardId, owner: PlayerId, before: ReadyGame,
      after: ReadyGame, viewer: Option[PlayerId]): Vector[LogSpan] =
    card(id, before, after, viewer) match
      case CardWord.Named(span) => Vector(span)
      case back: CardWord.Back =>
        val row = before.game.current.players.find(_.player == owner)
        val (noun, index) = id match
          case _: RelicId =>
            ("relic", row.fold(-1)(_.relics.indexWhere(_.id == id)))
          case _ =>
            ("adviser", row.fold(-1)(_.advisers.indexWhere(_.id == id)))
        if index < 0 then one(back)
        else Vector(LogSpan.Text(s"facedown $noun (slot ${index + 1})"))

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

  /** A power note's card list (catalog batch 2, N5): up to
    * [[LogWords.Inline]] cards as one phrase, as `cards` writes it. More are
    * a `Cards` span a client opens, each card its face or its back, or plain
    * "6 cards" when the viewer may identify none of them. */
  def listed(ids: Vector[CardId], states: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] =
    val words = ids.map(seen(_, states, viewer))
    if ids.size <= LogWords.Inline then cards(words)
    else if words.forall(_.isInstanceOf[CardWord.Back]) then
      Vector(LogSpan.Text(s"${ids.size} cards"))
    else Vector(LogSpan.Cards(ids.zip(words).map((id, word) => face(id, word))))

  /** A card as a card list shows it: its face when named, else its back. */
  private def face(id: CardId, word: CardWord)
      : oathdigital.protocol.projection.CardDetailsProjection = word match
    case CardWord.Named(_) => presentation.cardDetails(id, None, hidden = false)
    case CardWord.Back(_) => presentation.hiddenCard(id match
      case _: RelicId => "relic"
      case other => presentation.cardKind(other))

  /** A power named by the card it is printed on, as the modifier decision
    * names it; the card follows the visibility rule on `ready`. A banner's
    * power, printed on no card, is named by its banner. */
  def power(ready: ReadyGame, actor: PlayerId, id: PowerId,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    printed.printedOn(ready, actor, id.value).fold(
      Vector[LogSpan](LogWords.bannerOf(id).fold[LogSpan](
        LogSpan.Text(presentation.safeLabel(id.value)))(banner)))(card =>
      one(this.card(card, ready, ready, viewer)))

private[gamelog] object LogWords:
  /** The banner whose face prints `id`: banner power ids begin
    * `banner.{key}.`. */
  def bannerOf(id: PowerId): Option[Banner] =
    Banner.all.find(banner => id.value.startsWith(s"banner.${banner.key}."))

  /** A roll as a dice span, or nothing for no dice, a mixed pool or a face
    * family neither die prints. */
  def dice(faces: Vector[DieFace]): Option[LogSpan.Dice] =
    val rolled = faces.flatMap(rolledFace)
    Option.when(rolled.size == faces.size && rolled.nonEmpty &&
      rolled.forall(_.die == rolled.head.die))(
      LogSpan.Dice(rolled.head, rolled.tail))

  /** `oathdigital.model.DieFace` is open, so a face family neither pool
    * rolls maps to nothing rather than to a guess. */
  private def rolledFace(value: DieFace): Option[RolledFace] = value match
    case AttackDieFace.HollowSword => Some(RolledFace.HollowSword)
    case AttackDieFace.OneSword => Some(RolledFace.OneSword)
    case AttackDieFace.TwoSwordsSkull => Some(RolledFace.TwoSwordsSkull)
    case DefenseDieFace.Blank => Some(RolledFace.Blank)
    case DefenseDieFace.OneShield => Some(RolledFace.OneShield)
    case DefenseDieFace.TwoShields => Some(RolledFace.TwoShields)
    case DefenseDieFace.Doubler => Some(RolledFace.Doubler)
    case _ => None

  /** The most cards a power note names inline (catalog batch 2, N5). */
  val Inline: Int = 5

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
