package oathdigital.gameplay.walker

import oathdigital.model.{Answered, DecisionAnswer, OathViolation, Operation,
  PlayerId, ProcedureRef, ReadyGame}

/** The verdicts of the searches one [[WalkerPowers]] instance has run (global
  * operation restrictions design, "Performance budget"). A command reads the
  * same park more than once: the walk narrows it before it parks, the answer
  * check narrows it again, and a projection or a preview reads it again. The
  * verdict of one answer there does not change, so it is kept.
  *
  * Only a verdict asked from the live walk is kept. A verdict asked from
  * inside a search also depends on the decisions that search already stands
  * at, and its cost is paid once inside the kept verdict above it.
  *
  * The memo belongs to one powers instance and holds at most `limit`
  * verdicts: when it is full it is emptied, so an instance that lives as
  * long as the application never holds more than that. A verdict depends on
  * the powers, the tree, the position, the state and the answers, so the key
  * names the tree by identity and everything else by value.
  */
private[walker] final class SearchMemo(limit: Int = 256):
  private val entries = new java.util.concurrent.ConcurrentHashMap[
    SearchMemo.Key, Either[OathViolation, Unit]]

  def verdict(base: SearchMemo.Base, answer: DecisionAnswer)(
      compute: => Either[OathViolation, Unit]): Either[OathViolation, Unit] =
    val key = SearchMemo.Key(base, answer)
    Option(entries.get(key)).getOrElse:
      val found = compute
      if entries.size >= limit then entries.clear()
      entries.put(key, found)
      found

private[walker] object SearchMemo:
  /** What every answer to one decision shares. Its hash is taken once, since
    * the state inside it is large. */
  final class Base(private val root: Operation,
      private val path: Vector[String], private val state: ReadyGame,
      private val answered: Vector[Answered],
      private val procedure: Option[ProcedureRef],
      private val active: PlayerId, private val decisionId: String,
      private val owner: PlayerId):
    private lazy val hash: Int = (System.identityHashCode(root), path, state,
      answered, procedure, active, decisionId, owner).##

    override def hashCode: Int = hash

    override def equals(other: Any): Boolean = other match
      case that: Base => (this eq that) || (hash == that.hash &&
        root.eq(that.root) && path == that.path && state == that.state &&
        answered == that.answered && procedure == that.procedure &&
        active == that.active && decisionId == that.decisionId &&
        owner == that.owner)
      case _ => false

  final case class Key(base: Base, answer: DecisionAnswer)
