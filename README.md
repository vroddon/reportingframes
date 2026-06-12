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
$env:CLAUDE_API_KEY = "sk-ant-..."
```

**Windows (Command Prompt)**
```cmd
set CLAUDE_API_KEY=sk-ant-...
```

**Linux / macOS**
```bash
export CLAUDE_API_KEY=sk-ant-...
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
java -cp "target/celestine.jar:target/libs/*" celestine.Main
```

Then open [http://localhost:8080](http://localhost:8080) in your browser.

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
│   ├── Main.java                   # Entry point, starts HTTP server on port 8080
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
