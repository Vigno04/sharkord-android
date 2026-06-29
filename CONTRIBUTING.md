# Contributing to Sharkord Android

First off, thank you for considering contributing to Sharkord Android! We welcome any contributions, whether they be feature requests, bug fixes, or documentation improvements.

## 🌿 Branching Model (Git Flow)
We strictly follow the **Git Flow** branching model.
- **`main`**: The stable branch reflecting the latest production release. **Never commit directly to this branch.**
- **`develop`**: The active development branch. Features and bug fixes are merged here before a release.
- **`feature/<name>`**: For new features. Branch off from `develop` and merge back into `develop`.
- **`bugfix/<name>`**: For fixing bugs. Branch off from `develop` and merge back into `develop`.
- **`release/<version>`**: For preparing a new release.

## 💻 How to Contribute
1. **Fork** the repository and clone it locally.
2. **Create a branch** using the Git Flow convention (e.g., `feature/add-dark-mode` or `bugfix/fix-login-crash`). Make sure to branch off from the `develop` branch!
3. **Make your changes** and commit them with clear, descriptive messages.
4. **Push** your branch to your fork.
5. **Open a Pull Request** against the `develop` branch. (Do NOT open PRs against `main`).

## ✅ Pull Request Guidelines
- Fill out the provided Pull Request template.
- Ensure your code follows the existing style and architecture.
- If adding a feature or fixing a bug that affects the UI, provide screenshots or a video.
- Test your changes locally to ensure they do not introduce new crashes.

## 🐛 Reporting Bugs
Use the GitHub Issues tab and select the **Bug Report** template. Please provide as much context as possible, including your device model, Android version, and steps to reproduce.
