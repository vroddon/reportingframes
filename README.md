# Celestine — FrameNet Annotator

A minimal Java web application that annotates English sentences with FrameNet frames using the Claude API.

## Supported Frames

| Frame | Core Frame Elements |
|---|---|
| **Conditional_scenario** | Profiled_possibility, Opposite_possibility, Consequence, Anti_consequence |
| **Request** | Speaker, Addressee, Recipient, Message, Message_argument, Medium |
| **Time_period_of_action** | Action, Agent, Duration |
| **Time_vector** | Direction, Distance, Event, Landmark_event |

## Requirements

- Java 11 or higher
- Maven 3.x
- A Claude API key (Anthropic)

## Setup

Set the environment variable before running:

**Windows (PowerShell)**
```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
```

**Windows (Command Prompt)**
```cmd
set ANTHROPIC_API_KEY=sk-ant-...
```

**Linux / macOS**
```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

## Build

```bash
mvn package
```

This produces `target/celestine.jar` and copies dependencies to `target/libs/`.

## Run

```bash
java -cp "target/celestine.jar;target/libs/*" celestine.Main
```

On Linux/macOS use `:` instead of `;`:

```bash
java -jar target/celestine.jar
```

Then open [http://localhost:8612](http://localhost:8612) in your browser.

## Usage

1. Paste an English sentence into the text box.
2. Click **Annotate** (or press Ctrl+Enter).
3. The dominant frame and its annotated Frame Elements are displayed as JSON.

## Output Format

```json
{
  "sentence": "If it rains, I will be wet",
  "frame": "Conditional_scenario",
  "target": { "text": "If", "start": 0, "end": 2 },
  "fes": [
    { "name": "Profiled_possibility", "text": "it rains", "start": 3, "end": 11 },
    { "name": "Consequence", "text": "I will be wet", "start": 13, "end": 26 }
  ]
}
```

## Project Structure

```
celestine/
├── input/                          # Input files (reserved)
├── src/main/java/celestine/
│   ├── Main.java                   # Entry point, starts HTTP server on port 8612
│   ├── StaticHandler.java          # Serves index.html
│   ├── AnnotateHandler.java        # POST /annotate endpoint
│   └── ClaudeClient.java           # Claude API wrapper + system prompt
├── src/main/resources/
│   └── index.html                  # Frontend
├── pom.xml
└── README.md
```

## IDE Support

The project is a standard Maven project and opens directly in:
- **NetBeans**: File → Open Project → select the `celestine` folder
- **VS Code**: Open folder + install the [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)


## Ideas

A collection of directions worth exploring as the project matures.

### Publish frames as an OWL/RDF ontology

Convert the frame definitions and annotations produced by Celestine into a proper OWL ontology, in the spirit of the **FrameNet Ontology** (Narayanan et al., 2005, v1.1, available at https://rhizomik.net/html/framenetonto/). That work expressed FrameNet frames as OWL classes and frame elements as properties, enabling reasoning over annotated content. A domain-specific variant focused on regulatory reporting provisions would be considerably smaller and more tractable than full FrameNet.

### Connect to Framester

**Framester** (Gangemi, Alam, Asprino, Presutti, Recupero — EKAW 2016; https://framester.github.io/) is a large-scale RDF/OWL linked data hub that integrates FrameNet, WordNet, VerbNet, PropBank and DBpedia into a single graph, with frames represented as OWL classes and frame elements as properties. It is the most complete existing RDF treatment of FrameNet and a natural upper layer for Celestine's domain-specific frame vocabulary. Linking Celestine annotations to Framester URIs would make them interoperable with any other resource already aligned to Framester. Framester also uses OntoLex-Lemon for its lexical layer: OntoLex-Lemon represents the lexical units that evoke frames (the words *"notify"*, *"submit"*, *"within"*) via `ontolex:evokes`, while the frame concepts themselves are expressed in OWL — the two vocabularies are complementary, not competing.

> Gangemi, A., Alam, M., Asprino, L., Presutti, V., & Recupero, D. R. (2016). *Framester: A Wide Coverage Linguistic Linked Data Hub.* In EKAW 2016, Springer LNAI 10024.

### Alignment with event ontologies

Explore alignment between the frames used here and existing event ontologies, following the approach of the paper **EventOA: An Event Ontology Alignment Benchmark Based on FrameNet and Wikidata**. Frames such as Request, Receiving and Conditional_scenario have natural counterparts in Wikidata and schema.org event vocabularies. A benchmark alignment would make Celestine annotations interoperable with the broader linked open data cloud.

### Connection to ContractFrames

Build on the work in **ContractFrames: Bridging the Gap Between Natural Language and Logics in Contract Law** (Rodríguez-Doncel et al.), which proposes a frame-based representation of contractual provisions. Regulatory reporting duties share significant structural overlap with contract obligations — the same frames (Request, Conditional_scenario, Time_period_of_action) recur. A joint frame vocabulary covering both legal domains would allow cross-corpus annotation and reasoning.

### Links to other resources

- **Akoma Ntoso / LegalDocML** — annotated provisions could be embedded as metadata inside AKN documents, linking frame annotations to the structural elements (article, paragraph, point) they describe.
- **ELI (European Legislation Identifier)** — frame annotations could be published as linked data referencing ELI URIs, enabling SPARQL queries such as *"find all provisions that evoke a Request frame where the Speaker is the Commission"*.
- **ALLOT / DOLCE** — Monica Palmirani's ALLOT ontology for legal text already models agents, roles and events; frames could be mapped onto ALLOT classes.
- **OntoLex-Lemon** — the W3C standard for representing lexical resources as linked data; the frame lexicon itself (LUs, FEs) could be published in this format.
- **PropBank / VerbNet** — complementary lexical resources that could be cross-linked to increase coverage of legal verbs not yet in the Celestine frame set.

### Frame interaction graph

Rather than treating each frame annotation as independent, model the relations between co-occurring frames within a provision as a graph — e.g. a Time_period_of_action node connected to a Request node with a *deadline_for* edge. This would make the temporal and conditional structure of reporting obligations explicit and queryable. See also the double-graph parsing approach in Zheng et al. (NAACL 2022).

## Comments:
"I would like to use FrameNet annotations to create something similar to a KG or a lightweight ontology, specifically for regulatory reporting provisions. The idea is to link Frames and Frame Elements together (like temporal parameters, agents, conditional clauses), to make annotated Frame knowledge more cohesive inside each regulatory reporting provision, instead of having distinct Frames that don't interact. I am still unsure if this is a feasible idea or not.


It would be similar to the work in this paper, but specifically tailored for RR provisions (and without the parsing because I don't have the skills):
Ce Zheng, Xudong Chen, Runxin Xu, and Baobao Chang. 2022. A Double-Graph Based Framework for Frame Semantic Parsing. In Proceedings of the 2022 Conference of the North American Chapter of the Association for Computational Linguistics: Human Language Technologies, pages 4998–5011, Seattle, United States. Association for Computational Linguistics."
