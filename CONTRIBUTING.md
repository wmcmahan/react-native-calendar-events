# Contributing

Contributions are always welcome, no matter how large or small!

We want this community to be friendly and respectful to each other. Please follow it in all your interactions with the project. Before contributing, please read the [code of conduct](./CODE_OF_CONDUCT.md).

## Development workflow

This project is a monorepo managed using [Yarn workspaces](https://yarnpkg.com/features/workspaces). It contains the following packages:

- The library package in the root directory.
- An example app in the `example/` directory.

To get started with the project, make sure you have the correct version of [Node.js](https://nodejs.org/) installed. See the [`.nvmrc`](./.nvmrc) file for the version used in this project.

Yarn 4 is pinned through the `packageManager` field, so enable [Corepack](https://nodejs.org/api/corepack.html) once with `corepack enable`. Then run `yarn` in the root directory to install the required dependencies for each package:

```sh
yarn
```

> Since the project relies on Yarn workspaces, you cannot use [`npm`](https://github.com/npm/cli) for development without manually migrating.

The [example app](/example/) demonstrates usage of the library. You need to run it to test any changes you make.

It is configured to use the local version of the library, so any changes you make to the library's source code will be reflected in the example app. Changes to the library's JavaScript code will be reflected in the example app without a rebuild, but native code changes will require a rebuild of the example app.

If you want to use Android Studio or Xcode to edit the native code, you can open the `example/android` or `example/ios` directories respectively in those editors. To edit the Objective-C or Swift files, open `example/ios/CalendarEventsExample.xcworkspace` in Xcode and find the source files at `Pods > Development Pods > react-native-calendar-events`.

To edit the Java or Kotlin files, open `example/android` in Android studio and find the source files at `react-native-calendar-events` under `Android`.

You can use various commands from the root directory to work with the project.

To start the packager:

```sh
yarn example start
```

To run the example app on Android:

```sh
yarn example android
```

To run the example app on iOS:

```sh
yarn example ios
```

To confirm that the app is running with the new architecture, you can check the Metro logs for a message like this:

```sh
Running "CalendarEventsExample" with {"fabric":true,"initialProps":{"concurrentRoot":true},"rootTag":1}
```

Note the `"fabric":true` and `"concurrentRoot":true` properties.

Make sure your code passes TypeScript:

```sh
yarn typecheck
```

To check for linting errors, run the following:

```sh
yarn lint
```

To fix formatting errors, run the following:

```sh
yarn lint --fix
```



### Scripts

The `package.json` file contains various scripts for common tasks:

- `yarn`: setup project by installing dependencies.
- `yarn typecheck`: type-check files with TypeScript.
  - `yarn lint`: lint files with [ESLint](https://eslint.org/).
    - `yarn example start`: start the Metro server for the example app.
- `yarn example android`: run the example app on Android.
- `yarn example ios`: run the example app on iOS.
  
### Sending a pull request

> **Working on your first pull request?** You can learn how from this _free_ series: [How to Contribute to an Open Source Project on GitHub](https://app.egghead.io/playlists/how-to-contribute-to-an-open-source-project-on-github).

When you're sending a pull request:

- Prefer small pull requests focused on one change.
- Verify that linters and tests are passing.
- Review the documentation to make sure it looks good.
- Follow the pull request template when opening a pull request.
- For pull requests that change the API or implementation, discuss with maintainers first by opening an issue.

### Native linting

```sh
yarn lint:ios      # swift-format in strict mode (bundled with Xcode); `yarn format:ios` fixes in place
yarn lint:android  # Android Lint with warnings as errors, plus Kotlin warnings as errors
```

Both run in CI. The strict settings apply only to these commands, never to a consumer app compiling the library.

### Expo plugin check

```sh
yarn test:expo   # packs the library, installs it into a throwaway Expo app, runs `expo prebuild`, asserts the permissions
```

Needs network for the Expo template. It also proves autolinking sees the published package layout, so run it after changing `package.json` `files` or `exports`. Not in CI yet.

### Native tests

The native cores have their own suites and both run in CI:

```sh
yarn test:ios      # XCTest via the CocoaPods test spec; boots a simulator and grants calendar access
yarn test:android  # Robolectric tests against an in-memory calendar provider
yarn test:android:emulator  # instrumented tests on a connected device or running emulator
```

To boot a headless emulator for that last one, `scripts/android-emulator.sh start` installs the system image and AVD on first use and waits for boot; `scripts/android-emulator.sh stop` shuts it down. CI uses the same script.

`yarn test:ios` needs `pod install` to have been run in `example/ios`. `yarn test:android` needs `JAVA_HOME` and `ANDROID_HOME` set.

The iOS logic lives in `ios/CalendarEventsStore.swift` and the Android logic in `android/src/main/java/com/calendarevents/CalendarEventsStore.kt`. Both are free of React Native imports on purpose: the Turbo Module adapters next to them only convert arguments and promises. Keep new calendar logic in the stores so it stays testable.

## Releasing

Releases are two steps: `release-it` tags locally, and the `Release` workflow publishes from CI.

```sh
yarn release --preRelease=alpha   # 3.0.0-alpha.0 -> 3.0.0-alpha.1, dist-tag alpha
yarn release                      # next stable version, dist-tag latest
```

`release-it` runs lint, typecheck and the unit tests, bumps `package.json`, commits, tags, pushes, and creates the GitHub release. It does not publish. Pushing the tag triggers `.github/workflows/release.yml`, which checks out that tag on a clean machine, re-runs the gates plus `yarn test:expo`, and runs `npm publish --provenance`.

Publishing authenticates with [npm trusted publishing](https://docs.npmjs.com/trusted-publishers): npm trusts this repository's `release.yml` workflow directly, so there is no npm token in GitHub secrets or on anyone's machine, and every version carries a provenance attestation. This needs a one-time registration by a package owner on npmjs.com: package settings, Trusted Publisher, GitHub, owner `wmcmahan`, repository `react-native-calendar-events`, workflow filename `release.yml`.

If the workflow fails, nothing is published and the tag already exists: fix the cause on the branch and re-run the workflow from the Actions tab rather than re-tagging.

