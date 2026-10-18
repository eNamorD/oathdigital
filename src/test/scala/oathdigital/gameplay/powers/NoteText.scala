package oathdigital.gameplay.powers

import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.walker.PowerNoted
import oathdigital.model._

/** A phase power's notes as plain sentences, for the card suites (power log
  * lines design, "Tests"). Arguments read plainly: a player, card or site by
  * id, an amount with its unit, a bank by suit, dice by their count. The
  * formatter's own rendering is tested in `GameLogPowerLinesSuite`. */
object NoteText:
  final case class Said(key: String, text: String, covers: Boolean)

  /** The notes `power` journaled in `events`, in order. */
  def said(power: PhasePower, events: Vector[OathEvent]): Vector[Said] =
    said(power.id, power.noteKeys, events)

  /** The notes the power `id`, declaring `keys`, journaled in `events`. */
  def said(id: PowerId, keys: Vector[NoteKey], events: Vector[OathEvent])
      : Vector[Said] =
    events.collect { case PowerNoted(`id`, note, covers) =>
      Said(note.key, sentence(keys, note), covers) }

  /** `note` as the plain sentence its key in `keys` words. */
  def sentence(keys: Vector[NoteKey], note: PowerNote): String =
    keys.find(_.name == note.key).fold(s"<no template for ${note.key}>")(
      _.template.map {
        case NotePart.Text(words) => words
        case NotePart.Arg(index) =>
          note.args.lift(index).fold("<missing>")(plain)
        case NotePart.Plural(index, one, many) => note.args.lift(index) match
          case Some(NoteArg.Amount(1, _)) | Some(NoteArg.Number(1)) => one
          case _ => many
      }.mkString)

  private def plain(arg: NoteArg): String = arg match
    case NoteArg.Player(id) => id.value
    case NoteArg.Card(id) => id.value
    case NoteArg.Site(id) => id.value
    case NoteArg.Amount(value, unit) => s"$value ${unit.word(value)}"
    case NoteArg.Number(value) => value.toString
    case NoteArg.Bank(suit) => s"the $suit bank"
    case NoteArg.Dice(faces) => s"${faces.size} dice"
    case NoteArg.Cards(ids) => ids.map(_.value).mkString(", ")
    case NoteArg.Banner(banner) => BannerRules.displayName(banner)
    case NoteArg.Pile(pile) => SearchSource.name(pile)
    case NoteArg.RegionName(region) => region.toString
    case NoteArg.SuitName(suit) => suit.toString
    case NoteArg.Goal(goal) => OathkeeperGoalChoice.named(goal)
    case NoteArg.Players(ids) => ids.map(_.value).mkString(", ")
    case NoteArg.Clauses(parts) => parts.map(clause).mkString(", ")
    case NoteArg.Placements(entries) => entries.map((count, site) =>
      s"$count on ${site.value}").mkString(", ")
    case NoteArg.Things(items) => items.map(thing).mkString(", ")

  private def clause(part: NoteClause): String = part match
    case NoteClause.BurnedFavor(count) => s"burned $count favor"
    case NoteClause.Discarded(ids) =>
      s"discarded ${ids.map(_.value).mkString(", ")}"
    case NoteClause.SetAside(ids) =>
      s"set aside ${ids.map(_.value).mkString(", ")}"

  private def thing(item: NoteThing): String = item match
    case NoteThing.Site(id) => id.value
    case NoteThing.Pawn(player) => s"${player.value}'s pawn"
    case NoteThing.Card(id) => id.value
    case NoteThing.Banner(banner) => BannerRules.displayName(banner)
