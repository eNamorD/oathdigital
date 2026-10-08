# Profiling the tests

`scripts/profile-tests.py` measures the test suite and finds where its time
goes. It writes everything under `target/profile/`.

## Time the gate

```bash
./sbtw root/test frontend/test
scripts/profile-tests.py --gate
```

The first command warms the incremental compile, so the measured run times
the tests and not a rebuild. The report, `target/profile/gate.txt`, gives:

- the gate's wall-clock and CPU time;
- the summed root suite time, which counts every test thread, so it is
  larger than the wall-clock time;
- the slowest root suites and tests, from the JUnit reports in
  `target/test-reports/` that the run wrote.

Frontend suites are not ranked: their JUnit reports carry no useful times,
and they run in about 11 s.

## Profile some suites

```bash
scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'
```

The arguments are root `testOnly` filters. The script runs them under Java
Flight Recorder (`target/profile/tests.jfr`), then writes
`target/profile/profile.txt` with the same ranking plus three frame lists.
All count only samples that ran project code:

- **Self frames** are the frames on top of the stack, in any package. A JDK
  frame here, such as `java.lang.ClassValue.getFromBackup`, names a cost that
  project code pays through a library call.
- **Watched frames** are the share of samples with one named frame anywhere
  on the stack: the card index, the state invariant, `gather` and
  `ClassValue`. The list is `WATCHED` in the script. Edit it to follow a
  different hot spot.
- **Inclusive frames** are the `oathdigital` frames anywhere on the stack. A
  frame at 26% was on the stack in a quarter of the samples, so removing its
  work saves up to a quarter of the time. The walker's own wrapper frames fill
  the top of this list; look for the first frame that does real work.

The recording keeps the top 128 stack frames of each sample. To see who calls
a hot frame, open `tests.jfr` in JDK Mission Control.

Profile a small, stable set of suites. The campaign power suites are the
usual choice: they dominate the slowest-suite list and spend most of their
time in `ProcedureWalker`.

## The search budget

`SearchBudget` times the walker's search on a Campaign start against a 50 ms
budget. Run it alongside a profile when a change touches the walker:

```bash
./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"
```

## Reading numbers

Wall-clock, CPU and summed suite times grow with machine load: another
session's build can add 30% or more. Each report prints the load average at
the start of the run. Compare times only between runs made on a quiet machine
(load average near 2 or less). The frame shares in a profile and the
`SearchBudget` timings move far less with load, so prefer them when the
machine is busy. Treat a change below about 5% as noise.
