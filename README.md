# SAYMA BUILDERS Android App

Android WebView app for https://saymabuilders.com/portal/.

## Facebook login configuration

The app can open the portal without Facebook native-login credentials. Native Facebook login is enabled only when a Facebook Client Token is supplied at build time. This prevents an incomplete Facebook SDK configuration from crashing the app at launch.

For a local build, provide the token as the `FACEBOOK_CLIENT_TOKEN` environment variable or the Gradle property `facebookClientToken`.

For GitHub Actions, add a repository Actions secret named `FACEBOOK_CLIENT_TOKEN`. If the secret is not configured, the APK still builds and the app opens the portal; the native Facebook login override remains disabled, so the website can use its normal login flow.
