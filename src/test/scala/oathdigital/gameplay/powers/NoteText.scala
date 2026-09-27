package oathdigital.gameplay.powers

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
    events.collect { case PowerNoted(id, note, covers) if id == power.id =>
      Said(note.key, sentence(power.noteKeys, note), covers) }

  private def sentence(keys: Vector[NoteKey], note: PowerNote): String =
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
