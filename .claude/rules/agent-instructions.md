# Agent Instructions

## Core Principles

- Maintain focused contexts for both the orchestrator (main agent) and each sub-agent
- Use the Task tool to delegate suitable tasks to sub-agents
- Optimize for task efficiency and token usage

## Critical Thinking

- Be critical of all suggestions, including your own
- Challenge proposed solutions before accepting them
- Verify assumptions against actual code
- Don't over-engineer - keep solutions minimal and focused

## When to Use Sub-Agents

**Use sub-agents for:**
- Exploring unfamiliar parts of the codebase
- Searching for patterns across multiple files
- Complex research tasks requiring multiple file reads
- Tasks that benefit from parallel execution

**Handle directly:**
- Simple file reads when you know the exact path
- Single-file edits with clear requirements
- Quick grep/glob searches
- Tasks where context from the conversation is essential

## Multi-Step Tasks

1. Break complex tasks into discrete steps
2. Use the TaskCreate/TaskUpdate tools to track progress
3. Complete one step fully before moving to the next
4. Verify each step's success before proceeding

## Exploring vs Implementing

- **Exploring**: Use read-only tools, gather context, understand patterns
- **Implementing**: Make minimal, focused changes based on exploration
- Don't mix exploring and implementing in the same step
- When uncertain, explore first

## Error Handling

- If a tool fails, understand why before retrying
- Don't repeat the same failing approach
- Ask for clarification when requirements are ambiguous
- Report blockers early rather than working around them silently
