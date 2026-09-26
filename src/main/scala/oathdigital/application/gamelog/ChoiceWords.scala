package oathdigital.application.gamelog

import oathdigital.application.WalkerDecisionProjector
import oathdigital.model._
import LogSpan.Text

/** A chosen option as the log names it: a player, site, card or banner as
  * its typed reference (a card following the visibility rule), a button as
  * the label its chooser was shown. */
private[gamelog] final class ChoiceWords(words: LogWords,
    decisions: WalkerDecisionProjector):
  def options(refs: Vector[DecisionOptionRef], before: ReadyGame,
      after: ReadyGame, viewer: Option[PlayerId]): Vector[LogSpan] =
    LogWords.join(refs.map(option(_, before, after, viewer)))

  def option(ref: DecisionOptionRef, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] = ref match
    case DecisionOptionRef.Button(key) =>
      Vector(Text(shown(before, ref).getOrElse(words.label(key))))
    case DecisionOptionRef.Player(id) => Vector(words.player(id))
    case DecisionOptionRef.Site(id) => Vector(words.site(id))
    case DecisionOptionRef.Denizen(id) => card(id, before, after, viewer)
    case DecisionOptionRef.Relic(id) => card(id, before, after, viewer)
    case DecisionOptionRef.Vision(id) => card(id, before, after, viewer)
    case DecisionOptionRef.Edifice(id) => card(id, before, after, viewer)
    case DecisionOptionRef.RelicSlot(owner, slot) =>
      Vector(words.player(owner), Text(s"'s facedown relic (slot ${slot + 1})"))
    case DecisionOptionRef.Banner(banner) => Vector(words.banner(banner))
    case DecisionOptionRef.Deck(deck) => Vector(Text(words.label(deck.key)))
    case DecisionOptionRef.FavorBank(suit) => Vector(Text(s"the $suit bank"))

  private def card(id: CardId, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    words.one(words.card(id, before, after, viewer))

  /** The label the parked decision gave `ref`. */
  private def shown(before: ReadyGame, ref: DecisionOptionRef)
      : Option[String] =
    decisions.parkedDecide(before).toVector.flatMap(_.query match
      case DecisionQuery.ChooseOne(options, _) => options
      case DecisionQuery.ChooseMany(_, _, options, _) => options
      case _ => Vector.empty).flatMap(labelled(ref)).headOption

  private def labelled(ref: DecisionOptionRef)(option: DecisionOption)
      : Option[String] = option match
    case DecisionOption.Button(button, label) if button == ref => Some(label)
    case DecisionOption.Priced(inner, _) => labelled(ref)(inner)
    case DecisionOption.Badged(inner, _) => labelled(ref)(inner)
    case _ => None
