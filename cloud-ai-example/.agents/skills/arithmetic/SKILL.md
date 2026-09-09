---
name: arithmetic
description: Evaluate arithmetic expressions using the calculator tool. Use when the user asks to calculate or compute math.
triggers: [calculate, compute, arithmetic, math]
---
# Arithmetic Skill

When the user asks to calculate or compute a math expression:

1. Extract the arithmetic expression from the user's request
2. Call the `calculator` tool with the expression
3. Report the result

## Examples
- "calculate 25 * 4" → call calculator("25 * 4") → report 100
- "what is 10 + 20" → call calculator("10 + 20") → report 30
