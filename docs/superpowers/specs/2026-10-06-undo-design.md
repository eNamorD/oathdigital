# Undo

**Status:** designed 2026-10-06. It replaces the roadmap's **Undo system**
item under Later. The roadmap item allowed undo only before an action
started; this design allows it inside a running walker and across an
action's end.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**draft set** and **situation**. This design adds **command batch**,
**barrier**, **void marker** and **taken-back group**.

## Goal

A player who misclicks can take the step back, as long as the step showed no
one anything new and no other player has answered since. A player who takes
back a step by mistake can restore it, as long as nothing else has happened.
Other players see what was taken back.

## Terms

- **Command batch.** The events one accepted command appends, in one atomic
  `repository.append`. Every parked-decision answer is its own batch. A batch
  can span procedures and a turn handover (`FinishRest` runs the round end
  and the next Wake in its own batch).
- **Issuer.** The player whose command produced a batch: the answering
  player (`ChoicePayload.by`) for `ResolveWalker`, the active player for
  every other command.
- **Live batch.** A batch that no void marker currently voids.
- **Barrier.** A batch that undo never takes back. Because undo only ever
  takes back the latest live batch, a barrier also shields every batch before
  it.
- **Void marker.** A journaled event that voids a range of earlier events
  (`StepTakenBack`) or restores one (`StepRestored`). A marker is not a
  command batch for undo purposes.
- **Taken-back group.** A contiguous run of voided Game Log lines, shown
  struck through and collapsed.

## Rulings

### Who may undo, and how far

1. Undo takes back exactly one command batch: the journal's latest live
   batch. Each press takes back one more.
2. Only that batch's issuer may undo it. This makes another player's answer
   a barrier for everyone else with no extra rule: once the defender answers,
   the latest live batch is the defender's, so the actor cannot reach past
   it.
3. The off-turn issuer of the latest live batch may undo it. A defender who
   misclicks a battle plan takes it back while nobody has acted since.
4. The actor may undo while the walker is parked on another player's
   decision, as long as that player has not answered. Their question
   disappears and their draft set empties, because the position changed.
5. Undo crosses action ends, End Wake and Begin Rest. After undoing every
   step of action B, the next press continues into action A.
6. No consent is asked. The barriers already protect what other players
   committed and what was revealed.
7. Undo always takes back the whole batch, including any automatic effects
   other players' powers produced inside it (Herald's favor, Sealing Ward's
   dice).

### Barriers

A batch is a barrier when any of these holds:

- It holds an operation that `WalkerSearch.hides` reports: `Roll`,
  `Shuffle`, `Draw`, `Peek`, `Reveal`, or a `Flip` to faceup. Undo shares
  that one predicate with the legality search, so the two cannot drift.
- It holds a legacy minor-action reveal: `SiteRelicsPeeked` or
  `OwnedRelicRevealed`.
- It is a `FinishRest` batch. The turn passes there, along with the round
  end, War Exhaustion's random fallback and the Wake victory checks. A
  `BeginRest` batch that finishes Rest on its own, because no REST power is
  usable, is a `FinishRest` batch too.
- It is part of game setup (everything up to the first player's first
  command).
- It sets the game result.

Consequences the playtest should expect:

- A started Search cannot be undone: its draw is in the start batch.
- A started Recover cannot be undone, and neither can a "continue" answer:
  each rolls in its own batch.
- The last answer before a Campaign roll cannot be undone, because the roll
  runs in the same batch.

### Redo

1. Redo restores the most recently voided batch. Each press restores one
   more, in the reverse order of the undos.
2. Only that batch's issuer may redo it.
3. The redo stack is the run of batches voided by the void markers since the
   last command batch. Any command batch clears it, whoever issues it.
4. Redo leaves no trace in the Game Log. The restored lines return to normal
   and leave their taken-back group. The outcome is the same as if the undo
   had never happened.

### Game Log

1. Voided lines stay where they were, struck through.
2. Each contiguous run of voided lines is one taken-back group, however many
   undo presses made it. A single voided line is still a group.
3. A group is collapsed by default. Its header names the players who took the
   moves back: "p1 took back the following moves:", or "p1 and p2 took back
   the following moves:" when a run mixes issuers.
4. Inside a group, lines keep the action-section styling they had.
5. A viewer sees a voided line exactly as they saw it before the undo. The
   formatter already filters each line by viewer, and voiding does not change
   that.

### Controls

1. The action pane shows **Undo** to the player who may undo now, and
   **Redo** to the player who may redo now. Nobody else sees either control.
2. The label is always "Undo", including when the press would take back an
   action's start.
3. A hover note names what the press would change, taken from the first Game
   Log line of the affected batch, for example "Undo: p1 chose battle plan
   Battle Cry". A batch with no line of its own falls back to "Undo your last
   step" ("Redo your last step").
4. There is no keyboard shortcut.
5. The client-side modifier flow's Back and Cancel stay as they are. They
   discard drafts that were never sent and are not undo.

## Architecture

### Storage: void markers on an append-only journal

The journal stays the single append-only history. Undo and redo append
events. They never delete or rewrite one.

- `StepTakenBack(from, until, by)` voids the events with sequences in
  `[from, until)`: the latest live batch.
- `StepRestored(from, until, by)` un-voids that range again.

Both carry their range explicitly so replay does not recompute it. Replay
still checks it: a marker whose range is not the latest live batch
(`StepTakenBack`) or the top of the redo stack (`StepRestored`), or whose
`by` is not the batch's issuer, fails replay like any other rejected event
fact.

The `EventStreamRepository` contract, the HSQLDB schema, `expectedNextSequence`
and `StaleClientPosition` are unchanged. Sequence numbers are never reused.

### Replay

`GameApplicationService.reconstruct` gains a pre-scan before it replays:

1. Decode the whole stream, as today.
2. Walk the markers in order and compute the set of voided ranges.
3. Replay every event that is neither a marker nor voided, in order, through
   the existing replay path.

Replaying live events from the start reproduces the state at the void point
exactly, because each batch was produced from the state its predecessors
left. No snapshot is stored. `WalkerReplay` needs no change: it never sees a
voided event.

The pre-scan also yields what undo and redo need: the batch boundaries, each
batch's issuer, the latest live batch, and the redo stack. `history` (which
feeds the Game Log) carries the voided ranges beside the replayed steps.

### Commands and authorization

- `GameIntent.Undo` and `GameIntent.Redo`, with no arguments, sent with the
  client's `expectedNextSequence` like every intent. `GameCommand` gains
  matching cases.
- Both are accepted while a walker is pending. Today only `ResolveWalker` and
  `RollWalker` are, so that gate widens to admit them.
- `Authorization` admits any seated player. The service rejects a press from
  anyone but the issuer of the affected batch, and a press with nothing to
  affect, with new `OathViolation` cases.
- Handling an undo appends only the marker. Nothing in the rules engine runs:
  the next load replays without the voided batch.

### Barrier detection

One function decides whether a batch is a barrier, from its decoded events
alone. It applies `WalkerSearch.hides` to the operations of every
`WalkerStepRecorded` step and to `RollPayload` steps, and it matches the
legacy reveal events, the `FinishRest` batch, the setup batches and the
result-setting events. The function lives beside the pre-scan in the
application layer, so the rules engine stays unaware of undo.

### Projection

Each viewer's projection gains `undo: Option[StepOffer]` and
`redo: Option[StepOffer]`. A `StepOffer` holds the hover note. Each is
present only for the player who may press it. It also gains `logRevision`,
the sequence of the latest void marker (or none).

### Game Log

- `GameLogFormatter` formats live and voided segments alike. It marks each
  entry of a voided segment with its taken-back group. A voided range always
  covers whole segments, because a segment is one command's append, so voiding
  never changes the lines around it.
- `LogEntry` gains the group it belongs to, if any. The group's issuers come
  from the markers that voided its batches.
- The client log grows by appending entries newer than the last
  `(sequence, ordinal)`. A void or restore changes existing lines, so when
  `logRevision` changes the client calls `resetLog()` and refetches the whole
  log. The seen marker in localStorage stays valid, because sequence numbers
  are never reused.

### Client sync

Polling accepts a snapshot only when its `nextSequence` is larger. Markers
grow the stream, so every client sees an undo on its next poll. A viewer's
draft set is bound to the position, so a parked question that disappears
takes its drafts with it, as it does today when the position moves.

### The facedown-adviser pick

The "Play facedown adviser" pick moves into the `play-facedown-adviser`
walker as its first parked decision: an ordinary board decision over the
player's facedown advisers. `StartWalker` then carries no pick. Today's
preselection carries over: with exactly one facedown adviser, its site is the
board draft. The pre-walker local choice, `FacedownAdviserDraft` and its
"Cancel action" button retire. Undo takes back the start instead.

## Slices

Each slice lands green and ships on its own.

1. **Engine, storage and server log.** The two markers and their codec, the
   pre-scan and voided replay, barrier detection, the Undo intent, command
   and authorization, the projection's `undo` and `logRevision`, and the
   formatter's taken-back groups.
2. **Frontend.** The Undo control and its hover note, the collapsible
   taken-back groups, and the client log reset on a changed `logRevision`.
3. **Redo.** `StepRestored`, the redo stack, the Redo intent and the
   projection's `redo`, and the Redo control.
4. **The facedown-adviser pick in the walker.** The board decision, the
   retirement of the pre-walker choice, and the removal of its Cancel.

## Testing

- Situation-based suites, one per barrier kind: a roll, a draw, a shuffle, a
  peek, a reveal, a faceup flip, a legacy reveal, `FinishRest`, setup and a
  result. Each checks that undo stops at the barrier.
- Chained undo: through every step of an action, across its end, into the
  action before.
- The off-turn issuer undoing their own answer, and the actor then undoing
  theirs.
- The actor undoing while the walker is parked on another player's decision.
- Rejections: a press by a non-issuer, a press with nothing to undo, a press
  on a barrier.
- Replay of journals with voided and restored ranges, including tampered
  markers, which must fail replay.
- Redo: restoring in order, the stack clearing on any command batch, and a
  press by a non-issuer.
- `WalkerReplayDriftSuite` stays green.
- The formatter groups contiguous voided runs, names mixed issuers, and
  restores lines on redo.
- jsdom: the Undo and Redo controls appear only for the right player, the
  hover note, the group toggle and the log reset.

## Left out

- A keyboard shortcut.
- Consent for undo. It belongs to **Phase - Consent system** if a table ever
  wants it.
- Undo past a barrier, with or without keeping the recorded outcome.
