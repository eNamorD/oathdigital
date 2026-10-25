package oathdigital.frontend

import oathdigital.model.PlayerColor

/** What a board's Supply figure says: on hover, the bank and the Supply the
  * next Rest gains; opened, the bank, the Supply track with the board's
  * marker and current band, and the Supply the next Rest returns. The
  * numbers are the server's (`PlayerBoard`), so this never re-derives a
  * rule; it only says it.
  *
  * "Bank", not "supply", for the warbands not yet on the table: Supply is
  * the resource this figure counts, and one word may not name both.
  */
private[frontend] object SupplyAtRest:
  val title = "Supply"

  private val citizen = "Citizens copy the Chancellor's Supply."

  /** What an Exile's figure opens: the bank and, under it, where the rest
    * of their warbands are; the track; then the Rest. */
  final case class Panel(bank: String, elsewhere: String,
      track: SupplyTracker.Track, rest: String)

  def isExile(role: String): Boolean = role.equalsIgnoreCase("exile")

  def note(role: String, board: PlayerBoard): String =
    val bank = s"Warbands in bank: ${board.bankedWarbands}"
    if isExile(role) then
      s"$bank\nSupply gain at Rest: +${board.restSupplyBase}"
    else s"$bank\n$citizen"

  /** A citizen's figure opens the one line that is their rule. */
  def citizenLines: Vector[String] = Vector(citizen)

  /** `own` is the viewer's board, which is "your" board; any other is
    * "their". */
  def panel(board: PlayerBoard, bands: Vector[SupplyBand], maximum: Int,
      color: PlayerColor, own: Boolean): Panel =
    Panel(
      s"Warbands in bank: ${board.bankedWarbands}",
      s"(${board.warbands} on ${if own then "your" else "their"} board, " +
        s"${board.siteWarbands} on sites)",
      SupplyTracker.Track(bands, board.supply, board.bankedWarbands, maximum,
        color),
      s"Supply gain at Rest: ${board.restSupplyBase} plus unspent Supply " +
        s"(max $maximum)")
