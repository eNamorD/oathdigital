package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{BurnTake, Burnt,
  ContributingPower}
import oathdigital.gameplay.powers.DeclaredNotes
import oathdigital.model.{Burn, CoreOperation, Location, PayCost, Piece,
  PlayerId, PositionedLocation, PowerId, ReadyGame, SecretSide, Take}

/** The burns that powers take (catalog batch 8, "A burn's actor"). Before a
  * batch runs, each burn a power claims is replaced by a `Take` of the same
  * tokens to its taker, so a restriction on a `Take` holds against it and
  * the count is cut as a burn's would be. A secret keeps its side: a burn
  * from a board takes faceup secrets unless it names the facedown side. A
  * burn of the taker's own board leaves the tokens where they are. A cost's
  * burnt portions leave its `PayCost` as `Take`s that must be paid in full.
  * A burn inside a `Take`'s leaving is replaced there. The first power to
  * claim a burn, in the order contributions chain, takes it. The pass names
  * no card.
  */
private[walker] object BurnTakes:
  /** A burn `power` claimed. */
  final case class Taken(power: PowerId, burnt: Burnt, take: BurnTake)

  /** A batch with its claimed burns replaced, and what was claimed. */
  final case class Swapped(operations: Vector[CoreOperation],
      taken: Vector[Taken]):
    /** One line per power, taker and burner, for what `executed` took. A
      * burn of the taker's own board counts in full: nothing moved. */
    def lines(executed: Vector[CoreOperation], actor: PlayerId,
        declared: Option[DeclaredNotes]): Vector[LogNoted] =
      val moved = takesIn(executed)
      taken.map(group).distinct.flatMap { key =>
        val claims = taken.filter(group(_) == key)
        val amount = claims.map(kept).sum + claims.filterNot(own)
          .map(source).distinct.map(took(moved, key._2, _)).sum
        Option.when(amount > 0)(
          claims.head.take.line(key._3, amount, actor))
          .flatMap(NoteCheck.journal(declared, key._1, _, covers = false))
      }

  /** `operations` with every burn a power claims replaced. */
  def replace(ready: ReadyGame, powers: Vector[ContributingPower],
      operations: Vector[CoreOperation]): Swapped =
    swapAll(ready, powers.sortBy(ContributingPower.sortKey), operations)

  /** Whether a power claims a burn of `operation`. */
  def takes(ready: ReadyGame, powers: Vector[ContributingPower],
      operation: CoreOperation): Boolean =
    replace(ready, powers, Vector(operation)).taken.nonEmpty

  private def swapAll(ready: ReadyGame, powers: Vector[ContributingPower],
      operations: Vector[CoreOperation]): Swapped =
    operations.foldLeft(Swapped(Vector.empty, Vector.empty)) {
      (done, operation) =>
        val next = swap(ready, powers, operation)
        Swapped(done.operations ++ next.operations, done.taken ++ next.taken)
    }

  private def swap(ready: ReadyGame, powers: Vector[ContributingPower],
      operation: CoreOperation): Swapped = operation match
    case burn: Burn =>
      burn.by.flatMap(by =>
        claim(ready, powers, Burnt(burn.resource, burn.from, by))) match
        case Some(taken) => Swapped(replacement(taken, required = false),
          Vector(taken))
        case None => Swapped(Vector(burn), Vector.empty)
    case pay: PayCost =>
      val board = PositionedLocation(Location.PlayArea(pay.player))
      def claimed(amount: Int, piece: Int => Piece.Counted) =
        Option.when(amount > 0)(Burnt(piece(amount), board, pay.player))
          .flatMap(claim(ready, powers, _))
      val favor = claimed(pay.cost.favorBurnt, Piece.Favor(_))
      val secrets = claimed(pay.cost.secretBurnt, Piece.Secrets(_))
      if favor.isEmpty && secrets.isEmpty then
        Swapped(Vector(pay), Vector.empty)
      else
        val cost = pay.cost.copy(
          favorBurnt = if favor.isDefined then 0 else pay.cost.favorBurnt,
          secretBurnt = if secrets.isDefined then 0 else pay.cost.secretBurnt)
        val taken = favor.toVector ++ secrets.toVector
        Swapped(pay.copy(cost = cost) +:
          taken.flatMap(replacement(_, required = true)), taken)
    case take: Take if take.leaving.nonEmpty =>
      val leaving = swapAll(ready, powers, take.leaving)
      Swapped(Vector(take.copy(leaving = leaving.operations)), leaving.taken)
    case other => Swapped(Vector(other), Vector.empty)

  /** The first power's claim on `burnt`. */
  private def claim(ready: ReadyGame, powers: Vector[ContributingPower],
      burnt: Burnt): Option[Taken] =
    powers.iterator.flatMap(power =>
      power.takesBurn(ready, burnt).map(Taken(power.id, burnt, _)))
      .nextOption()

  /** The `Take` in place of a claimed burn, or nothing when the tokens are
    * on the taker's own board. */
  private def replacement(taken: Taken, required: Boolean)
      : Vector[CoreOperation] =
    if own(taken) then Vector.empty
    else
      val from = taken.burnt.from
      Vector(Take(taken.burnt.resource, taken.take.taker, from.location,
        Location.PlayArea(taken.take.taker), from.position,
        required = required, sourceSide = side(taken.burnt)))

  /** The side a burn of secrets from a board takes: the one it names, or
    * faceup. */
  private def side(burnt: Burnt): Option[SecretSide] = burnt match
    case Burnt(_: Piece.Secrets, PositionedLocation(_: Location.PlayArea,
        _, named), _) => named.orElse(Some(SecretSide.FaceUp))
    case _ => None

  private def own(taken: Taken): Boolean =
    taken.burnt.from.location == Location.PlayArea(taken.take.taker)

  /** What a claim on the taker's own board counts. */
  private def kept(taken: Taken): Int =
    if own(taken) then taken.burnt.resource.amount else 0

  private def group(taken: Taken): (PowerId, PlayerId, PlayerId) =
    (taken.power, taken.take.taker, taken.burnt.by)

  /** Where a claim's take comes from: location, side and kind. */
  private def source(taken: Taken): (Location, Option[SecretSide], Boolean) =
    (taken.burnt.from.location, side(taken.burnt),
      taken.burnt.resource.isInstanceOf[Piece.Secrets])

  /** Every `Take` in `operations`, inside a `Take`'s leaving too. */
  private def takesIn(operations: Vector[CoreOperation]): Vector[Take] =
    operations.flatMap {
      case take: Take => take +: takesIn(take.leaving)
      case _ => Vector.empty
    }

  /** The count the takes in `moved` took for `taker` from `from`. */
  private def took(moved: Vector[Take], taker: PlayerId,
      from: (Location, Option[SecretSide], Boolean)): Int =
    moved.collect {
      case Take(piece: Piece.Counted, `taker`, location, _, _, _, _, named)
          if location == from._1 && named == from._2 &&
            piece.isInstanceOf[Piece.Secrets] == from._3 => piece.amount
    }.sum
