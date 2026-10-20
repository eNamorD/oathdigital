# How Far a Hidden-Outcome Check Looks: Two Questions, Not Three Scopes

> Status: decided and implemented 2026-10-19, from the architecture review of
> 2026-10-13, which raised this as candidate J and rated it worth exploring.
> It records what the review got right, what its framing got wrong, the one
> real defect behind it, and the answer to the question that will recur —
> whether a deferred roll is hidden. The predicate's case list is untouched
> here.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **barrier**, **command
batch** and **roll feedback**.

## What the review found

`HiddenOutcomes.hides` had four callers, walking the operation tree at three
different depths. The review concluded that there was no single answer to
"what is hidden right now", and proposed one traversal with the depth as a
parameter.

The count was right. All four callers existed and all three depths differed.
The inference from it was wrong.

## Why the framing failed

The review counted call sites. It did not ask which traversal each site
rides, and that is the whole question.

Two of the four sites are inside the walk itself:
`ProcedureWalker.walkComposite` and `ProcedureWalker.runLeaf`. Neither is
making a depth choice. The walk already recurses, so every child is reached
on its own and judged on its own; asking about a subtree there would be
redundant, and it would stop the search above information it is entitled to
prune past. Those two are one traversal's per-node hook, and self-only is the
correct and deliberate answer for both.

The other two sites hold a batch they are about to run, or that already ran:
`WalkerSteps.runBuildOps` and `application/UndoBarrier`. Nothing recurses for
them. `OperationApplication` runs an operation's `Operation.flatten` leaves,
so a composite's children are never reached on their own, and the only
question worth asking is whether anything beneath the operation hides.

So there are two questions, not three depths, and which one a caller asks
follows from what it holds rather than from a choice it makes. A depth
parameter would have put that choice back in the caller's hands at four
sites, where a wrong value is silent — which is the defect, restated as
configuration.

## The defect

`UndoBarrier` asked the batch question correctly, in a private helper of its
own. `WalkerSteps.runBuildOps` asked the node question over a batch:
`ops.exists(hides)`, top level only. A hidden operation nested in a composite
the batch carries was not reported, so a search ran it and pruned on what it
learnt.

`Take.leaving` is what makes this reachable. `Take` is a domain composite
carrying a caller-supplied `Vector[CoreOperation]`
(`model/CoreOperations.scala`), rewritten recursively by `BurnTakes`, and
filled by procedures and powers. A power author putting a `Reveal` there
would have broken the legality search with nothing to say so.

Nothing in the repository did. The whole engine suite was run with
`runBuildOps` instrumented to report any built operation that does not hide
at its own node but hides beneath it; across 4383 tests the only report came
from the probe written to provoke it. The production builders of `leaving`
(`CampaignRaid`, `ConspiracyWhenPlayed`) carry moves and a secrets burn. The
defect was latent, and is fixed before it was reached.

The earlier shape of this gap is now closed by the type system rather than by
this decision: until the
[operation algebra depth design](2026-10-14-operation-algebra-depth-design.md),
`Sequence` was a `CoreOperation` and could sit in a built batch directly.
Control operations left that family on 2026-10-14, so only Oath's own
composites can nest a hidden operation in a batch today.

## The decision

`HiddenOutcomes` states both questions and names them:

- `hides(operation)` — whether this node itself shows something the chooser
  cannot see. `private[walker]`, because a caller outside the walk holds a
  batch and the node question is the wrong one there. The compiler enforces
  this; no architecture test is needed for it.
- `hidesAnywhere(operation)` — whether anything the pipeline will run under
  this operation hides: the node, or any leaf beneath it. Public, and the
  only question the two batch-level callers ask.

`WalkerSearch.hides`, a bare pass-through, is gone; the walk's two sites call
`HiddenOutcomes.hides` directly. `UndoBarrier`'s private helper is gone;
`hidesAnywhere` is exactly it. The undo design's promise that undo and the
search "cannot drift" now names `hidesAnywhere`, which is the function they
actually share.

## Whether a deferred roll is hidden

The review named a deferred roll as the concrete defect. It is invisible to
every caller, which is what the review saw, and that is correct rather than
broken.

`Defer(key, ops)` is a leaf, so `Operation.flatten` stops at it and never
descends into `ops`. That is what holding them means: nothing under a `Defer`
runs, is screened, or counts as a hidden outcome. A deferred roll reveals
nothing at the defer, because it does not happen there. It happens when a
later step of the same action returns those operations as operations of its
own — and it is reported then, by the walk's own per-node check or by
`hidesAnywhere`, according to how that step carries them.

The same holds for undo. A batch that only deferred a roll showed nobody
anything, so it is not a barrier, and the batch that later runs the roll is.

`Defer`'s definition now says this, because the question reads like a bug
every time it is asked.

## Consequences

- A caller outside `gameplay/walker` can no longer ask the node-level
  question. The guard the undo design promised in prose is now the
  compiler's.
- `DeltaPayload.ops` holds whole resolved operations, composites among them,
  not the leaves they flatten to. Its doc comment said the opposite, which
  argued for deleting the very `flatten` that the undo barrier depends on.
  Corrected.
- `HiddenOutcomesSuite` holds the predicate's cases, including the `Reroll`
  case that had been left in `ProcedureWalkerRerollSuite`.
  `HiddenOutcomeDepthSuite` holds the regression: one peek, three positions,
  and only the nested one changes behaviour with the fix.
