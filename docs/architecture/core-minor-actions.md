> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design.

# Core minor actions boundary

This slice implements the unmodified first-game procedures from Combined Rulebook p. 27 in `gameplay/actions/MinorActions.scala`. They are Act-phase minor actions and cost 0 Supply by default. Every submitted choice resolves atomically and passes through the normal action-boundary evaluation pipeline.

- Playing a facedown adviser starts the `play-facedown-adviser` walker with no start selection. Any executable Search modifiers are ordered before the start, as for Search. The walk's first decision, `cardplay.facedown-adviser`, is a board choose-one over the player's facedown advisers; the client opens it with the adviser picked when only one is held. The picked card's placement follows: the card-play subtree Search uses, revalidated by `CardPlay` against the loaded state. There is no pre-walker Cancel; Undo takes the start back.
- Peeking records the exact ordered relic identities at the pawn's site. Replayed knowledge is projected only to that player and filtered against relics still there. The UI keeps known site relics facedown at rest and temporarily reveals their inspectable detail on hover, keyboard focus, or press-and-hold; other scopes receive no identity-bearing card detail. Revealing flips one held facedown relic faceup.
- Site-to-board movement leaves one actor warband at the pawn's site. Board-to-site movement additionally requires the actor to rule it. Events record prior board and site counts.

Authenticated intents are actor-free; membership derives the player. Loopback commands retain an explicit development actor. Inactive, public, and other-player projections contain no minor-action state or controls.
