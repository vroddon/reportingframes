# Celestine — FrameNet Annotator

A minimal Java web application that annotates English sentences with FrameNet frames using the Claude API.

## Supported Frames

Frames are listed in priority order (the annotator returns up to 3 per sentence, with higher-priority frames first):

| Frame | Core Frame Elements |
|---|---|
| **Telling** | Speaker, Addressee, Message |
| **Request** | Speaker, Addressee, Recipient, Message, Message_argument, Medium |
| **Receiving** | Donor, Recipient, Theme |
| **Conditional_scenario** | Profiled_possibility, Opposite_possibility, Consequence, Anti_consequence |
| **Time_period_of_action** | Action, Agent, Duration |
| **Time_vector** | Direction, Distance, Event, Landmark_event |
| **Frequency** | Event, Interval |
| **Calendric_unit** | Unit, Name, Relative_time |

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

**Framester** (Gangemi, Alam, Asprino, Presutti, Recupero — EKAW 2016) is a large-scale RDF/OWL linked data hub created at CNR / Università di Bologna. It integrates FrameNet, WordNet 3.0, VerbNet 3.1, BabelNet, DBpedia, Yago and DOLCE-Zero into a single strongly connected knowledge graph, with frames formalised as OWL classes and frame elements as properties. This goes beyond a simple RDF dump of FrameNet: the formal OWL treatment enables full-fledged reasoning and SPARQL querying across all connected resources simultaneously.

Framester exposes a live SPARQL endpoint at https://w3id.org/framester/sparql and its data is available at https://github.com/framester/Framester. A companion service, Word Frame Disambiguation (WFD), maps words to frames via WordNet/BabelNet synsets and can serve as a cross-check against Claude's annotations.

**Celestine already uses Framester URIs** for its Turtle export: each detected frame is serialised as a reference to its Framester URI (e.g. `fst:Request`, `fst:Receiving`), making every downloaded `.ttl` file directly interoperable with the Framester ecosystem. A single SPARQL query to Framester's endpoint can then reveal the ontological neighbourhood of any annotated frame — parent frames, inherited FEs, related frames — without any additional code.

The connection to DOLCE-Zero is particularly relevant for the Celestine team: ALLOT, the legal ontology developed by Monica Palmirani's group at Università di Bologna, is also grounded in DOLCE, creating a natural bridge between Celestine's frame annotations and ALLOT's model of legal agents, roles and events.

> Gangemi, A., Alam, M., Asprino, L., Presutti, V., & Recupero, D. R. (2016). *Framester: A Wide Coverage Linguistic Linked Data Hub.* In EKAW 2016, Springer LNAI 10024.

### Alignment with event ontologies

Explore alignment between the frames used here and existing event ontologies, following the approach of the paper **EventOA: An Event Ontology Alignment Benchmark Based on FrameNet and Wikidata**. Frames such as Request, Receiving and Conditional_scenario have natural counterparts in Wikidata and schema.org event vocabularies. A benchmark alignment would make Celestine annotations interoperable with the broader linked open data cloud.

### Connection to ContractFrames

Build on the work in **ContractFrames: Bridging the Gap Between Natural Language and Logics in Contract Law** (Rodríguez-Doncel et al.), which proposes a frame-based representation of contractual provisions. Regulatory reporting duties share significant structural overlap with contract obligations — the same frames (Request, Conditional_scenario, Time_period_of_action) recur. A joint frame vocabulary covering both legal domains would allow cross-corpus annotation and reasoning.

### Links to other resources

- **Akoma Ntoso / LegalDocML** — annotated provisions could be embedded as metadata inside AKN documents, linking frame annotations to the structural elements (article, paragraph, point) they describe.
- **ELI (European Legislation Identifier)** — frame annotations could be published as linked data referencing ELI URIs, enabling SPARQL queries such as *"find all provisions that evoke a Request frame where the Speaker is the Commission"*.
- **ALLOT / DOLCE** — Monica Palmirani's ALLOT ontology for legal text already models agents, roles and events; frames could be mapped onto ALLOT classes.
- **PropBank / VerbNet** — complementary lexical resources that could be cross-linked to increase coverage of legal verbs not yet in the Celestine frame set.

### FrameNet 1.7 dataset

The FrameNet 1.7 corpus (frames, lexical units, frame-to-frame relations, annotated sentences) can be downloaded from:
https://www.kaggle.com/datasets/nltkdata/framenet?resource=download

It is also installable directly via NLTK:
```python
import nltk
nltk.download('framenet_v17')
```

The dataset is the basis for any graph-based extension of Celestine (RGCN, embeddings, link prediction).

### Reproduce or extend KID (Zheng et al., NAACL 2022)

The double-graph framework (KID) described in the Comments section is publicly available:
https://github.com/PKUnlp-icler/KID

It achieves Full structure F1 of 81.7 on FN 1.5 and 82.2 on FN 1.7 (BERT variant). The GloVe variant runs without a GPU and is a good starting point. An interesting open question for future work is whether KID trained on general FN 1.7 transfers to regulatory legal text, or whether the domain shift degrades performance — and how a hybrid approach combining KID with Celestine's LLM-based annotations could close that gap.

### Frame interaction graph

Rather than treating each frame annotation as independent, model the relations between co-occurring frames within a provision as a graph — e.g. a Time_period_of_action node connected to a Request node with a *deadline_for* edge. This would make the temporal and conditional structure of reporting obligations explicit and queryable. See also the double-graph parsing approach in Zheng et al. (NAACL 2022).

## Comments:
"I would like to use FrameNet annotations to create something similar to a KG or a lightweight ontology, specifically for regulatory reporting provisions. The idea is to link Frames and Frame Elements together (like temporal parameters, agents, conditional clauses), to make annotated Frame knowledge more cohesive inside each regulatory reporting provision, instead of having distinct Frames that don't interact. I am still unsure if this is a feasible idea or not.


It would be similar to the work in this paper, but specifically tailored for RR provisions (and without the parsing because I don't have the skills):
Ce Zheng, Xudong Chen, Runxin Xu, and Baobao Chang. 2022. A Double-Graph Based Framework for Frame Semantic Parsing. In Proceedings of the 2022 Conference of the North American Chapter of the Association for Computational Linguistics: Human Language Technologies, pages 4998–5011, Seattle, United States. Association for Computational Linguistics."
