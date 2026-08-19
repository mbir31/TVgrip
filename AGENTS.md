# Persistent Instructions for TVGrip

Whenever the user makes any request or submits changes in the chat, immediately after completing the requested task, ALWAYS perform these 3 mandatory follow-up tasks every single turn:

1. **Bug & Issue Verification & Fix**:
   - Thoroughly inspect the codebase for any potential runtime issues, nullability risks, missing handlers, Bluetooth/TLS edge cases, or regressions, and fix them.
   - Verify with tests and full compilation.

2. **README.md Modernization**:
   - Keep `README.md` completely up-to-date with all newly added features, pairing instructions, Bluetooth capabilities, architecture diagrams, and release badges.

3. **Clean GitHub Actions Workflow Maintenance**:
   - Ensure old/redundant GitHub workflow files under `.github/workflows/` are removed.
   - Maintain a single, updated, battle-tested `.github/workflows/release.yml` that automatically builds, signs, and publishes the installable `TVGrip.apk` into the repository's GitHub Releases tab upon every git push to `main` without manual intervention.
