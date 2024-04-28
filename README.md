## CFPQ_JavaGraphMiner

JacoDB-based utility for extracting graphs from Java programs for CFPQ-based analyses.

## Usage

To extract graphs used to [evaluate CFPQ_PyAlgo](https://github.com/FormalLanguageConstrainedPathQuerying/CFPQ_PyAlgo/tree/murav/optimize-matrix/cfpq_eval) 
execute the following command in the project root.
```bash
./gradlew run
```

After gradle task completes, open `CFPQ_JavaMiner/graph` folder, where you will find:
* extracted graphs in the format recognizable by [optimized CFPQ_PyAlgo](https://github.com/FormalLanguageConstrainedPathQuerying/CFPQ_PyAlgo/tree/murav/optimize-matrix)  
* mapping files needed to convert vertex, label and type ids back to Java elements they originated from (fields, expressions, types, etc.)
* type and super type data for every vertex that can be used for type-aware analyses (experimental, subject to change)

## Supported analyses

As of now `CFPQ_JavaGraphMiner` only supports extracting graphs for:
* field-sensitive context-insensitive points-to analysis with dynamic dispatch, but without reflection
  * these graphs are intended to be used with [this grammar](https://formallanguageconstrainedpathquerying.github.io/CFPQ_Data/grammars/data/java_points_to.html#java-points-to)

## Implementation overview

The high-level pipeline is as follows:
* [JacoDB](https://jacodb.org/) converts JVM-bytecode to three-address instruction (`JcInst`)
* `PtResolver` converts three-address instruction (`JcInst`) to points-to graph model (`PtModel`)
* **optional step**: `PtSimplifier` simplifies points-to graph model (`PtModel`) by eliminating some `assign` edges
* `IdGenerator` is used to assign identifiers to points-to graph model (`PtModel`) entities (i.e. create mappings)
* `GraphMiner` encodes points-to graph model (`PtModel`) using these mappings and saves encoded model and mappings

## Tests

Graph miner is covered with integration tests that:
* collect graphs for sample programs
* simplify these graphs to remove implementation dependent vertices
* assert equality of simplified graphs and manually verified ground-truth graphs

To run tests execute the following command in the project root.
```bash
./gradlew test
```

## Technologies

* Kotlin
* JacoDB
* JUnit 5
* Gradle
