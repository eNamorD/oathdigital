package oathdigital.gameplay.walker

import oathdigital.gameplay.OathViolation
import oathdigital.model.{DecisionAnswer, DecisionOptionRef, DecisionQuery}

/** The whole generic decision contract: whether a declared query is
  * answerable at all, and whether a submitted answer satisfies it.
  *
  * It lives in gameplay rather than beside the vocabulary it validates
  * because it returns typed [[OathViolation]]s, which the model may not name
  * (plan ruling R3), and in its own object rather than in `ProcedureWalker`
  * because that file is already at the line cap.
  *
  * Note what is absent: neither function takes a `ReadyGame`. That is
  * deliberate and load-bearing. These functions compare references and
  * nothing else, so they CANNOT express state-dependent legality even by
  * accident — which is what stops the per-decision validation closure this
  * vocabulary exists to delete from growing back here. Staleness is handled
  * structurally instead: the tree carrying the query is rebuilt against
  * authoritative state on every command, so an option that no longer exists
  * is simply absent from the query and the answer naming it is rejected
  * below.
  *
  * Prompt copy never appears in either function — not an option's label,
  * not a section's, and not the heading or confirm label a query titles its
  * panel with. Every match below discards those fields explicitly. An
  * author or a power may restate a prompt without changing what is
  * submittable, which is the whole reason an answer records references
  * rather than options.
  *
  * Neither function throws; a malformed query and a mismatched answer are
  * both `InvalidEventOrder` naming the decision.
  */
object DecisionQueries {

  /** Structural check on a declared query: the failure here is a bug in an
    * action tree, not a bad submission.
    *
    * Two properties are enforced, and they are different in kind. The first
    * is that the query can be answered at all: it must offer options,
    * references must be unique (a duplicate would make one submission
    * ambiguous), section keys must be unique, minima must be non-negative,
    * and the minima together must not demand more placements than there are
    * options — summed as `Long`, because minima are bounded only by what a
    * tree declares and an `Int` sum of two large ones wraps negative, which
    * would slip past the very check it fails.
    *
    * The second is that the query is worth asking. An action must not park
    * and prompt a player for an answer that is already determined, so a
    * partition needs at least two sections, and no single section may
    * demand every option. Those two rules are exactly the forced shapes:
    * given satisfiable minima and two or more sections, a partition has one
    * legal answer if and only if some section's minimum equals the option
    * count. A query that collapses to a forced shape against live state is
    * an action that should have omitted the node and applied the placement
    * itself.
    *
    * A choose-one with a single option is deliberately NOT rejected. A lone
    * button is a consent step rather than a choice — the player is being
    * asked to act, not to pick — and the node is also where a power window
    * hangs.
    */
  def wellFormed(decisionId: String,
      query: DecisionQuery): Either[OathViolation, Unit] = query match {
    case DecisionQuery.ChooseOne(options, _) =>
      val refs = options.map(_.ref)
      for {
        _ <- require(refs.nonEmpty, decisionId, "declares no options")
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
      } yield ()

    case DecisionQuery.Partition(sections, options, _, _) =>
      val refs = options.map(_.ref)
      val keys = sections.map(_.key)
      for {
        _ <- require(refs.nonEmpty, decisionId, "declares no options")
        _ <- require(sections.size >= 2, decisionId,
          "declares fewer than two sections")
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- require(keys.distinct.size == keys.size, decisionId,
          "declares duplicate sections")
        _ <- sections.find(_.minRequired < 0) match {
          case Some(section) => reject(decisionId,
            s"declares section '${section.key}' with a negative minimum")
          case None => Right(())
        }
        _ <- require(
          sections.map(_.minRequired.toLong).sum <= refs.size.toLong,
          decisionId, "declares section minimums no answer can meet")
        _ <- sections.find(_.minRequired == refs.size) match {
          case Some(section) => reject(decisionId,
            s"declares section '${section.key}' as taking every option, " +
              "leaving nothing to decide")
          case None => Right(())
        }
      } yield ()
  }

  /** Check on a submitted answer: the failure here is a bad or stale
    * submission, not a bug in the tree.
    *
    * The checks run in a fixed order so that each way of getting an answer
    * wrong reports its own message rather than whichever check happened to
    * fire first.
    */
  def accepts(decisionId: String, query: DecisionQuery,
      answer: DecisionAnswer): Either[OathViolation, Unit] = query match {
    case DecisionQuery.ChooseOne(options, _) => answer match {
      case DecisionAnswer.ChooseOneAnswer(selected) =>
        require(options.map(_.ref).contains(selected), decisionId,
          "does not offer the selected option")
      case _ =>
        reject(decisionId, "expects a single-choice answer")
    }

    case DecisionQuery.Partition(sections, options, _, _) => answer match {
      case DecisionAnswer.PartitionAnswer(placements) =>
        acceptsPartition(decisionId, sections.map(s => s.key -> s).toMap,
          options.map(_.ref), placements)
      case _ =>
        reject(decisionId, "expects a partition answer")
    }
  }

  private def acceptsPartition(decisionId: String,
      sections: Map[String, oathdigital.model.DecisionSection],
      declared: Vector[DecisionOptionRef],
      placements: Vector[oathdigital.model.DecisionPlacement])
      : Either[OathViolation, Unit] = {
    val placed = placements.map(_.option)
    for {
      _ <- require(placed.forall(declared.contains), decisionId,
        "does not offer a placed option")
      _ <- placements.map(_.sectionKey).find(!sections.contains(_)) match {
        case Some(key) => reject(decisionId, s"has no section '$key'")
        case None => Right(())
      }
      _ <- require(placed.distinct.size == placed.size, decisionId,
        "places an option more than once")
      _ <- require(declared.forall(placed.contains), decisionId,
        "leaves an option unplaced")
      counts = placements.groupBy(_.sectionKey).view.mapValues(_.size).toMap
      _ <- sections.values.toVector.sortBy(_.key)
        .find(s => counts.getOrElse(s.key, 0) < s.minRequired) match {
          case Some(section) => reject(decisionId,
            s"leaves section '${section.key}' below its minimum of " +
              section.minRequired)
          case None => Right(())
        }
    } yield ()
  }

  private def require(condition: Boolean, decisionId: String,
      detail: String): Either[OathViolation, Unit] =
    if (condition) Right(()) else reject(decisionId, detail)

  private def reject(decisionId: String,
      detail: String): Either[OathViolation, Unit] =
    Left(OathViolation.InvalidEventOrder(s"decision $decisionId $detail"))
}
