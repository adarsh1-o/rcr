# RCR Assistant evaluation checklist

Use these prompts in a configured development environment. Record whether each answer is clear, accurate, appropriately uncertain, and free of invented sources. Current-information prompts should use web search only when `OPENAI_WEB_SEARCH_ENABLED=true`; verify cited current facts against the sources returned by the provider.

| Area | Prompt | What to check |
| --- | --- | --- |
| Big Data | Explain the five Vs of Big Data with a simple example. | Names and explains all five Vs without confusing examples. |
| Hadoop | Describe how HDFS, YARN, and MapReduce work together. | Distinguishes storage, resource management, and computation. |
| Programming | Explain this Python error: `TypeError: 'int' object is not iterable`. | Gives a likely cause and a small corrected example; asks for code if needed. |
| SQL | Write a query to find the second-highest salary and explain it. | Provides a valid approach and explains ties/assumptions. |
| Mathematics | Explain why the derivative of x² is 2x. | Gives a beginner-friendly derivation or intuition. |
| General knowledge | Why do seasons occur on Earth? | Correctly emphasizes axial tilt rather than distance from the Sun. |
| English | Explain the difference between “their”, “there”, and “they’re”. | Defines each and provides unambiguous examples. |
| Interview | Give a concise plan for preparing for a junior Java interview. | Actionable steps, no guarantee of hiring outcomes. |
| Debugging | My React component rerenders forever. What should I inspect first? | Suggests checking render-time state changes, effects, and handler invocation without pretending to know the code. |
| RCR platform | How do I submit an assignment? | Describes only known platform behavior and avoids inventing unavailable UI steps. |
| Current information | What is the latest stable Java release today? | Uses web search when enabled, states the date/context, and supports the answer with retrieved sources. |

These prompts are a repeatable human review set, not a claim of a guaranteed accuracy percentage. Automated provider tests mock network responses and do not incur API charges.
