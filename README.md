<img width="724" height="140" alt="image" src="https://github.com/user-attachments/assets/7991c1f1-e8ad-4e90-8284-90cc572e25ed" />

# elrond

A powerful and flexible search query engine, inspired by [Scryfall](https://scryfall.com/docs/syntax).

## How it works (roughly)

1. A search query is provided as input (e.g. `t:instant or t:sorcery`)
2. The tokenizer splits the query into it's individual parts (e.g. `t`, `:`, `instant`, `or`, `t`, `:`, `sorcery`)
3. The tokens are combined into a valid search expression, based on the filters configured, while ensuring a valid syntax and dropping any invalid or unexpected tokens
4. The search expression gets simplified and normalized where possible (dropping unnecessary nesting, filters that contradict each other, ...)
5. The search expression is converted into a modular SQL expression
6. The SQL expression is executed and the result is mapped to our data models
