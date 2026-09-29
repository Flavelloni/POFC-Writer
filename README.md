# Pineapple OFC Scorekeeper

A Kobweb + Compose HTML app for scoring Pineapple Open Face Chinese Poker.

## Run

```powershell
.\gradlew.bat :site:kobwebStart
```

Then open `http://localhost:8080`.

Useful checks:

```powershell
.\gradlew.bat :site:compileKotlinJs
.\gradlew.bat :site:kobwebListRoutes
.\gradlew.bat :site:kobwebExport
```

## GitHub Pages

The included GitHub Actions workflow exports the Kobweb app and deploys it to GitHub Pages on every push to `main`.

After enabling GitHub Pages with **Source: GitHub Actions** in the repository settings, the app is served at:

`https://flavelloni.github.io/POFC-Writer/`

The royalties page is served at:

`https://flavelloni.github.io/POFC-Writer/royalties.html`

## Project Layout

- `site/.kobweb/conf.yaml` contains Kobweb server configuration.
- `site/src/jsMain/kotlin/com/pofc/AppEntry.kt` is the Kobweb app entry.
- `site/src/jsMain/kotlin/com/pofc/pages/Index.kt` contains the scorekeeper UI.
- `site/src/jsMain/kotlin/com/pofc/pages/Royalties.kt` contains the royalties reference page.
- `site/src/jsMain/kotlin/com/pofc/model/PofcModel.kt` contains the deck, hand parser, scoring, royalties, and persistence models.

## Features

- Start separate 2-player or 3-player sessions.
- Choose Standard scoring or OBK scoring.
- Track total score, latest round changes, and fantasy land counts.
- Enter rough hand descriptions such as `pair of kings`, `straight 7 high`, `two pair aces and sevens`, `full house 8 over 3`, or `quads 4`.
- The app asks for more detail only when a payout or hand order cannot be determined.
- Edit or delete previous rounds to correct mistakes.
- View a royalties page and the generated 52-card deck.

## Storage

Sessions are stored in browser `localStorage`, so refreshes and browser restarts keep the current device's data. Normal scorekeeping data is tiny; even thousands of rounds are usually far below browser storage limits.
