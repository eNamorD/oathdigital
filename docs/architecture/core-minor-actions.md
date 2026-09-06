> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design:
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`.

# Core minor actions boundary

This slice implements the unmodified first-game procedures from Combined Rulebook p. 27 in `gameplay/actions/MinorActions.scala`. They are Act-phase minor actions and cost 0 Supply by default. Every submitted choice resolves atomically and passes through the normal action-boundary evaluation pipeline.

- Facedown advisers launch one Search-derived client draft. The player orders any server-projected executable Search modifiers, chooses an adviser when needed, then submits one actorless `ResolveFacedownAdviser` intent containing discard or a faceup adviser/site placement. Drafting is not authoritative: the application revalidates journal position, actor, modifier identity/order, ownership/orientation, restrictions, Homeland replacement, site-play favor, and replacement before recording the existing deterministic outcome events. Facedown placement is not in this intent's result vocabulary. Currently the audited first-game catalog exposes no executable optional Search modifiers, so the same shell correctly presents an empty modifier step; unsupported printed handlers remain explicit blockers.
- Peeking records the exact ordered relic identities at the pawn's site. Replayed knowledge is projected only to that player and filtered against relics still there. The UI keeps known site relics facedown at rest and temporarily reveals their inspectable detail on hover, keyboard focus, or press-and-hold; other scopes receive no identity-bearing card detail. Revealing flips one held facedown relic faceup.
- Site-to-board movement leaves one actor warband at the pawn's site. Board-to-site movement additionally requires the actor to rule it. Events record prior board and site counts.

Authenticated intents are actor-free; membership derives the player. Loopback commands retain an explicit development actor. Inactive, public, and other-player projections contain no minor-action state or controls.
