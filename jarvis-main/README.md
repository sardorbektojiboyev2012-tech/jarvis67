# JARVIS Android project

## Build an installable test APK with GitHub Actions

1. Upload the contents of this folder to your **private** GitHub repository.
2. In GitHub, open **Actions** and choose **Build JARVIS APK**.
3. Run the workflow (or push a commit to `main`).
4. When it succeeds, open the run and download the `JARVIS-debug-APK` artifact.
5. Extract the artifact ZIP to get `app-debug.apk`, then install it on your Android phone.

The workflow builds a **debug APK** so it can be installed for testing without a personal signing key. This project has not been compiled in this chat environment, so the first GitHub Actions run is still required to reveal any remaining source-code errors.

## Optional YouTube setup

See `SETUP_YOUTUBE.md`. Set `GOOGLE_CLIENT_ID` under **Settings → Secrets and variables → Actions** if you want to configure YouTube sign-in. The app's local commands do not require a paid AI API.

## Important security note

Do not commit `jarvis.jks`, API keys, OAuth client secrets, or access tokens to GitHub. A debug APK is suitable for testing; publishing a release APK requires a separate secure signing setup.
