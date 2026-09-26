# Decision Form Tag Separation: Two Vocabularies That Share Spellings

> Status: decision recorded 2026-09-25, from an architecture review. It records
> why the projection's **form** tags and the journal's answer tags are not
> unified behind one constant, so a later review does not re-propose it. Taken
> alongside the [typed decision form design](2026-09-25-typed-decision-form-design.md),
> which types the question half of the projection wire. No implementation is
> authorized by this document alone.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **form** and **parked
decision**.

## Decision

Five spellings — `choose-one`, `choose-many`, `choose-amount`, `partition`,
`distribute` — appear in three independent tables, and they stay independent:

1. The projection's **form** tags, written by `WalkerDecisionProjector` and
   read by `ActionProjectionCodec`. Transient: server to client, rebuilt per
   request.
2. The answer half of the command wire, `DecisionAnswerWire`, whose tags are
   inline literals in `CommandNestedCodecs`. Transient: client to server.
3. The journal's answer tags, eight named constants in `DecisionAnswerCodec`.
   Durable, forward-only.

No shared constant, no shared enum, no generated table joins them. A review
that notices the duplication should read this file rather than remove it.

## Why

**The three wires have different lifetimes, and one of them cannot be
changed.** The projection has never been persisted: `DecisionQueryProjection`
appears nowhere under `persistence` or `serialization`, its encode and decode
have two call sites both inside `ActionProjectionCodec`, and the working
journal contains zero occurrences of `"form"`. Its tags are free to be
renamed or restructured. The journal is the opposite: the working journal holds
376 answer tags across its event envelopes, `DecisionAnswerCodec` decodes them
strictly, and journals are forward-only with no migration path. Renaming a
journal tag is not a refactor; it is a data migration with no recorded repair
procedure.

**A shared constant is the exact mechanism by which the safe rename becomes
the unsafe one.** The value of unifying would be that a spelling exists once.
The cost is that a rename made for a good reason on the transient side — a form
renamed because the domain word changed — reaches 376 durable rows as a silent
side effect, in a commit whose diff shows one string. The duplication is
visible and inert; the coupling would be invisible and live.

**The module layout would have to bend to allow it.** `shared/src/main` may
import only `oathdigital.protocol`, enforced by the architecture check.
`DecisionAnswerCodec` is an outward serialization adapter in `src/main`. A
constant shared between them would have to live in `shared`, which makes a
durable journal format depend on a type whose purpose is a transient
projection, and inverts the direction the codebase states: shared protocol
types are wire DTOs, not domain authority.

**The drift this would prevent has not happened and is cheap to catch another
way.** All three tables agree today. If the agreement is worth enforcing,
enforce it with a test that asserts the sets match — a failing test names the
divergence and leaves the fix to a human — rather than with a constant that
makes divergence impossible by making a dangerous rename easy.

## Considered and rejected

- **One shared enum for all three tables.** Rejected: couples a durable format
  to a transient one, needs a type in `shared` that the journal codec imports,
  and turns a projection-side rename into a journal migration.
- **Shared named constants for the tag strings only, codecs otherwise
  untouched.** Rejected, and this was the reviewer's first recommendation
  before the journal evidence was in. It has the same failure mode as the
  shared enum with none of the type safety: the rename still propagates, and
  the diff is smaller and easier to approve.
- **Unify the two transient tables only, leaving the journal alone.** Rejected
  for now, and the weakest of the three rejections. The question and answer
  halves are different vocabularies that happen to overlap: the answer half has
  eight cases including `propose-terms`, `accept-deal` and `decline-deal`,
  which are not forms at all. Unifying the overlapping five and leaving three
  outside is a worse shape than two clean tables. Revisit only if the two sets
  converge.
- **A test asserting all three sets match.** Not rejected — recommended if the
  drift is judged worth catching. It is not part of the typed form slice
  because that slice already adds the per-case round trips, and this assertion
  answers a different question.

## Consequences

- Architecture reviews should not propose unifying these tables on the grounds
  that a string is spelled three times. That it is spelled three times is
  recorded here and is not new information.
- The eight `DecisionAnswerCodec` tags are load-bearing on disk. Treat a change
  to any of them as a journal format change, not a rename.
- The projection's form tags remain free to change shape, which is what makes
  the typed form slice possible without a migration.
- Revisit if the journal gains a migration facility, or if a real divergence
  between the tables is observed in production. A divergence found by a test is
  a bug to fix in one table, not evidence that the tables should merge.
