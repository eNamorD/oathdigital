package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{AdviserFace, CardPlay, CardPlayRules,
  DiscardPermission, VisionRules}
import oathdigital.gameplay.operations.{OperationConsent,
  OperationRestrictions}
import oathdigital.model._

/** One way to play a card: the option the placement decision offers, and
  * where the planner puts the card when it is chosen. A Replace option's
  * placement names the card it discards.
  */
final case class CardSlot(option: DecisionOption, placement: SearchPlacement):
  def ref: DecisionOptionRef = option.ref

  /** The site card this play discards to make room, for a Replace site
    * slot. */
  def replacedAtSite: Option[CardId] = placement match
    case SearchPlacement.Site(replaced) => replaced
    case _ => None

/** The follow-up of a Replace site slot: the replaced card, shown chosen; the
  * other site cards the permissions left may still cover; and how many more
  * may go. */
final case class MoreDiscards(locked: DecisionOption,
    options: Vector[DecisionOption], most: Int)

/** Where a card may be played, as card slots (card play slots design, "Slot
  * builder").
  *
  * The options, in order: the discard pile; the site's first empty slot,
  * then, when a permission allows a site discard, each site card marked
  * Replace; the faceup plays;
  * the facedown plays. An adviser play is checked against the advisers as
  * they would be after it (`CardPlayRules.fits`): it offers a new slot when
  * that fits, and only that; or else each held adviser marked Replace whose
  * replacement fits, an adviser that does not count shown unavailable with
  * its reason; and nothing when no replacement fits. An adviser play after
  * which a note of `CardPlayRules` applies is shown `Warned` with its
  * reason, and stays on offer. A facedown adviser offers only its own slot,
  * faceup. A Vision played faceup goes to the revealed Vision spot, and a
  * Conspiracy played faceup is the "Play" button.
  *
  * Each option is planned with `CardPlay.operations`, and the printed
  * restrictions in force judge the plan (`judge`): an option only a waivable
  * protection refuses is askable of its ruler, an option another protection
  * refuses is shown unavailable with its reason, and an option the planner
  * or another restriction refuses is left out. Powers' own restrictions (Vow
  * of Obedience) are the walker look-ahead's to apply.
  *
  * Last, the rules' placement limits (`CardPlayRules.placementLimits`)
  * show each play they refuse unavailable with their reason. When they
  * leave no play open, the discard pile stays open.
  */
object CardSlots:
  /** The answer that plays a faceup Conspiracy, which takes no slot. */
  val play: DecisionOptionRef.Button = DecisionOptionRef.Button("play")

  /** Why a printed protection refuses a play, by its restriction's code. */
  private val reasons: Map[String, String] = Map(
    "locked" -> "Locked: cannot be discarded",
    "active-modifier" -> "In use as a modifier this action",
    "discard-immune" -> "Protected by the Hall of Ministers")

  def slots(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: CardPlay.Origin, rules: CardPlayRules,
      picked: Vector[CardId] = Vector.empty): Vector[CardSlot] =
    ready.game.current.players.find(_.player == actor).toVector.flatMap {
      player =>
        val candidates = discardPile(ready, actor).toVector ++
          siteSlots(catalog, ready, player, card, rules) ++
          faceup(player, card, origin, rules) ++
          facedown(player, card, origin, rules)
        limited(candidates.flatMap { (option, placement) =>
          judge(catalog, ready, actor, option, CardPlay.operations(catalog,
            ready, actor, card, placement, origin), picked)
            .map(CardSlot(_, placement))
        }, rules)
    }

  /** `slots` under the placement limits of `rules`: a play a limit
    * refuses is shown unavailable with that limit's reason. When the
    * limits leave no play open, the discard pile stays as it was, since
    * a card that cannot be played is discarded. */
  private def limited(slots: Vector[CardSlot], rules: CardPlayRules)
      : Vector[CardSlot] =
    val marked = slots.map(slot => rules.refusal(slot.placement)
      .filter(_ => slot.option.available).fold(slot)(reason =>
        slot.copy(option = DecisionOption.Unavailable(slot.option,
          reason))))
    if marked.exists(_.option.available) then marked
    else marked.zip(slots).map((after, before) =>
      if before.placement == SearchPlacement.Discard then before
      else after)

  /** The site discard permissions of `actor`'s play of `card`: the Homeland
    * rule's first, when the actor's site is the Homeland of the card's suit,
    * then the powers'. */
  def permissions(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, rules: CardPlayRules)
      : Vector[DiscardPermission] =
    val homeland = for
      site <- ready.game.current.players.find(_.player == actor)
        .flatMap(_.pawnSite)
      suit <- catalog.suitOf(card)
      if CardPlay.homelandSuit(catalog, site).contains(suit)
    yield CardPlayRules.homeland(site, actor)
    homeland.toVector ++ rules.siteDiscards

  /** The follow-up of `slot`, a Replace site slot: the other site cards,
    * an edifice included, and how many more the permissions left allow;
    * `None` when nothing more may be discarded. */
  def moreDiscards(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot,
      picked: Vector[CardId] = Vector.empty): Option[MoreDiscards] =
    val permitted = permissions(catalog, ready, actor, card, rules)
    val held = siteCards(ready, actor)
    for
      replacedId <- slot.replacedAtSite
      replaced <- held.find(_.id == replacedId)
      if permitted.size > 1
      options = held.filter(_.id != replacedId).flatMap(other =>
        judge(catalog, ready, actor, optionOf(other), CardPlay.operations(
          catalog, ready, actor, card, slot.placement, origin,
          Vector(other.id)), picked))
      most = math.min(permitted.size - 1, options.count(_.available))
      if most >= 1
    yield MoreDiscards(optionOf(replaced), options, most)

  /** The line of the permission each discarded site card spent, in discard
    * order: permissions are spent in the order `permissions` gives them, and
    * every one covers any site card. Refused when more cards are discarded
    * than there are permissions. */
  def credits(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, rules: CardPlayRules, discarded: Vector[CardId])
      : Either[OathViolation, Vector[Note]] =
    val permitted = permissions(catalog, ready, actor, card, rules)
    Either.cond(discarded.size <= permitted.size,
      permitted.take(discarded.size).map(_.note),
      OathViolation.InvalidSearchPlacement(
        "more site cards are discarded than the permissions in play allow"))

  /** `option` as the restrictions in force judge the plan it stands for,
    * played by `actor`. It is kept as it is when nothing refuses. When only
    * waivable refusals stop it, it is kept askable of their granters
    * (consent design, slice 3). Otherwise it is shown unavailable with a
    * protection's reason, or left out. */
  def judge(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      option: DecisionOption,
      planned: Either[OathViolation, Vector[CoreOperation]],
      picked: Vector[CardId] = Vector.empty): Option[DecisionOption] =
    if !option.available then Some(option)
    else planned.toOption.flatMap { operations =>
      val restrictions = OperationRestrictions.forCatalog(catalog)
        .active(Vector.empty, ready.game.current.walkerModifiers, picked)
      val refusals = OperationConsent.refusals(ready, operations,
        restrictions)
      // A refusal no consent can lift names the blocker, whatever order
      // the restrictions run in.
      val shown = refusals.find(_.waiver.isEmpty).orElse(refusals.headOption)
      if refusals.isEmpty then Some(option)
      else OperationConsent.ask(ready, actor, refusals)
        .map(DecisionOption.Unavailable(option, _))
        .orElse(shown.flatMap(refusal => reasons.get(refusal.code).map(
          reason =>
            DecisionOption.Unavailable(option, OptionReason.Written(reason)))))
    }

  private def discardPile(ready: ReadyGame, actor: PlayerId)
      : Option[(DecisionOption, SearchPlacement)] =
    CardPlay.discardRegion(ready, actor).map(region =>
      DecisionOption.DiscardPile(DecisionOptionRef.DiscardPile(region)) ->
        SearchPlacement.Discard)

  private def siteSlots(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    (for
      siteId <- player.pawnSite
      site <- ready.game.current.map.sites.get(siteId)
      capacity <- catalog.site(siteId).map(_.capacity)
    yield
      def slot(index: Int): DecisionOption =
        DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(siteId, index))
      val permitted = permissions(catalog, ready, player.player, card, rules)
      val empty = Option.when(site.denizens.size < capacity)(
        slot(site.denizens.size) -> SearchPlacement.Site(None))
      val replacing = if permitted.isEmpty then Vector.empty
        else site.denizens.zipWithIndex.map((held, index) =>
          DecisionOption.Replacing(slot(index), held.id) ->
            SearchPlacement.Site(Some(held.id)))
      empty.toVector ++ replacing).getOrElse(Vector.empty)

  private def faceup(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    val placement = SearchPlacement.Adviser(Orientation.FaceUp, None)
    card match
      case VisionRules.Conspiracy =>
        Vector(DecisionOption.Button(play, "Play") -> placement)
      case _: VisionId =>
        val spot = DecisionOption.RevealedVision(
          DecisionOptionRef.RevealedVision(player.player))
        Vector(player.revealedVision.fold[DecisionOption](spot)(held =>
          DecisionOption.Replacing(spot, held.id)) -> placement)
      case _ => adviserSlots(player, card, origin, Orientation.FaceUp, rules)

  private def facedown(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] = origin match
    case CardPlay.Origin.FacedownAdviser => Vector.empty
    case CardPlay.Origin.TemporaryHand =>
      adviserSlots(player, card, origin, Orientation.FaceDown, rules)

  private def adviserSlots(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, orientation: Orientation, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    val held = player.advisers
    val faces = held.map(AdviserFace.of)
    val played = AdviserFace(card, orientation)
    def slot(index: Int): DecisionOption = DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(player.player, index,
        orientation == Orientation.FaceUp))
    def noted(option: DecisionOption, after: Vector[AdviserFace])
        : DecisionOption =
      rules.noteOn(after, played).fold(option)(
        DecisionOption.Warned(option, _))
    origin match
      case CardPlay.Origin.FacedownAdviser =>
        // The card turns faceup where it lies.
        val own = held.indexWhere(_.id == card)
        if own < 0 then Vector.empty
        else
          val after = faces.updated(own, played)
          if !rules.fits(after) then Vector.empty
          else Vector(noted(slot(own), after) ->
            SearchPlacement.Adviser(orientation, None))
      case CardPlay.Origin.TemporaryHand =>
        val added = faces :+ played
        if rules.fits(added) then Vector(noted(slot(held.size), added) ->
          SearchPlacement.Adviser(orientation, None))
        else replacements(faces, played, rules).map((index, fits) =>
          val option = DecisionOption.Replacing(slot(index), held(index).id)
          (if fits then noted(option, faces.updated(index, played))
          else DecisionOption.Unavailable(option,
            OptionReason.Written(CardPlayRules.notCounted))) ->
            SearchPlacement.Adviser(orientation, Some(held(index).id)))

  /** The held advisers a play with no free slot may replace, by index, and
    * whether the area after the replacement fits. One that does not fit is
    * kept only when it does not count, to be shown unavailable: replacing it
    * frees no room. Nothing when no replacement fits. */
  private def replacements(faces: Vector[AdviserFace], played: AdviserFace,
      rules: CardPlayRules): Vector[(Int, Boolean)] =
    val fitting = faces.indices.toVector.map(index =>
      index -> rules.fits(faces.updated(index, played)))
    if !fitting.exists(_._2) then Vector.empty
    else fitting.filter((index, fits) =>
      fits || rules.uncounted(faces(index)))

  private def siteCards(ready: ReadyGame, actor: PlayerId)
      : Vector[SiteDenizenState] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(ready.game.current.map.sites.get)
      .fold(Vector.empty[SiteDenizenState])(_.denizens)

  private def optionOf(card: SiteDenizenState): DecisionOption = card match
    case denizen: DenizenState =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(denizen.id))
    case edifice: EdificeState =>
      DecisionOption.Edifice(DecisionOptionRef.Edifice(edifice.id))
