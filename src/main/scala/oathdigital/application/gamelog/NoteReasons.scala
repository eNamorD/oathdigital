package oathdigital.application.gamelog

import oathdigital.application.GamePresentationProjector
import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{PlayerId, PowerId, PowerNote, ReadyGame}

/** A power's note as one line of plain text: the reason an option it hid is
  * not on offer (board decisions design, "Unavailable options"). It is
  * worded exactly as the Game Log words the same note, with its cards
  * judged at the state the decision is asked in.
  */
private[application] final class NoteReasons(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector, wordings: NoteWordings):
  def this(catalog: ExecutableCatalog,
      presentation: GamePresentationProjector) =
    this(catalog, presentation, NoteWordings.default(catalog))

  private val lines = new PowerLines(new LogWords(catalog, presentation),
    wordings)

  def text(power: PowerId, note: PowerNote, ready: ReadyGame,
      viewer: Option[PlayerId]): Option[String] =
    lines.spoken(power, note, Vector(ready), viewer)
      .map(_.map(_.text).mkString)
