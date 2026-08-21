# Core minor actions boundary

This slice implements the unmodified first-game procedures from Combined Rulebook p. 27 in `gameplay/actions/MinorActions.scala`. They are Act-phase minor actions and cost 0 Supply by default. Every submitted choice resolves atomically and passes through the normal action-boundary evaluation pipeline.

- Facedown advisers may be discarded to the next region or played faceup as if searched. Restrictions, Homeland replacement, site-play favor, and discarded-card facts are replay checked. Printed `When Played`, Search-modifier, and Conspiracy behavior remain an explicit typed unsupported boundary in `MinorActionPowerSupport`; its fixed-catalog inventory fingerprint prevents changed handler vocabulary from being silently ignored. This transitional registry is replaced by executable handler registration in Phase 3.
- Peeking records the exact ordered relic identities at the pawn's site. Replayed knowledge is projected only to that player and filtered against relics still there. Revealing flips one held facedown relic faceup.
- Site-to-board movement leaves one actor warband at the pawn's site. Board-to-site movement additionally requires the actor to rule it. Events record prior board and site counts.

Authenticated intents are actor-free; membership derives the player. Loopback commands retain an explicit development actor. Inactive, public, and other-player projections contain no minor-action state or controls.
