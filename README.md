## CFPQ_JavaGraphMiner

JacoDB-based utility for extracting graphs from Java programs for CFPQ-based analyses.

## Usage

To extract graphs used to [evaluate CFPQ_PyAlgo](https://github.com/FormalLanguageConstrainedPathQuerying/CFPQ_PyAlgo/tree/murav/optimize-matrix/cfpq_eval), 
execute the following command in the project root.
```bash
./gradlew run
```

After the gradle task completes, open the `CFPQ_JavaMiner/graph` folder, where you will find:
* extracted graphs in the format recognizable by [optimized CFPQ_PyAlgo](https://github.com/FormalLanguageConstrainedPathQuerying/CFPQ_PyAlgo/tree/murav/optimize-matrix);
* mapping files needed to convert vertex, label, and type IDs back to the Java code elements they originated from (fields, expressions, types, etc.);
* type and supertype data for every vertex that can be used for type-aware analyses (experimental, subject to change).

## Supported Analyses

As of now, `CFPQ_JavaGraphMiner` only supports extracting graphs for _field-sensitive, context-insensitive points-to analysis with dynamic dispatch, but without reflection_.
These graphs are intended to be used with [this grammar](https://formallanguageconstrainedpathquerying.github.io/CFPQ_Data/grammars/data/java_points_to.html#java-points-to).

## Implementation Overview

The high-level pipeline is as follows:
* [JacoDB](https://jacodb.org/) converts JVM bytecode to three-address instructions (`JcInst`).
* `PtResolver` converts three-address instructions (`JcInst`) to a points-to graph model (`PtModel`).
* **Optional step**: `PtSimplifier` simplifies the points-to graph model (`PtModel`) by eliminating some `assign` edges.
* `IdGenerator` is used to assign identifiers to points-to graph model (`PtModel`) entities (i.e., create mappings).
* `GraphMiner` encodes the points-to graph model (`PtModel`) using these mappings and saves the encoded model and mappings.

## Tests

The graph miner is covered with integration tests that:
* collect graphs for sample programs;
* simplify these graphs to remove implementation-dependent vertices;
* assert the equality of simplified graphs and manually verified ground-truth graphs;

To run tests, execute the following command in the project root.
```bash
./gradlew test
```

## Technologies

* Kotlin
* JacoDB
* JUnit 5
* Gradle
