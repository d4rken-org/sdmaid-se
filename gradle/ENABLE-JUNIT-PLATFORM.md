# Enable JUnit Platform and JUnit XML reports

This file contains recommended snippets to enable JUnit Platform (JUnit5), the Vintage engine for JUnit4 compatibility, and JUnit XML report generation for CI. Do NOT apply blindly — review and merge into the appropriate build files (app/build.gradle.kts and root build.gradle) if they match your project structure.

## app/build.gradle.kts (Kotlin DSL)

Add inside the `android { ... }` block:

```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
        all {
            useJUnitPlatform()

            reports {
                junitXml.isEnabled = true
                html.isEnabled = true
            }

            testLogging {
                events("passed", "skipped", "failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
                showStandardStreams = false
            }
        }
    }
}
```

And add these test dependencies into the `dependencies { ... }` block:

```kotlin
// JUnit4 support
testImplementation("junit:junit:4.13.2")

// JUnit5
testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")

// Vintage engine to run JUnit4 tests on the JUnit Platform
testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.0")

// Optional: Kotest if used
// testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
// testImplementation("io.kotest:kotest-assertions-core:5.7.2")
```

## Root build.gradle (Groovy) — apply to JVM subprojects

Add to the root `build.gradle` to ensure non-Android modules use the JUnit Platform and produce XML reports:

```groovy
subprojects {
    tasks.withType(Test).configureEach {
        useJUnitPlatform()
        reports {
            junitXml.enabled = true
            html.enabled = true
        }
    }
}
```

## Notes
- Review existing project build files before merging. If your project uses Groovy DSL, adapt the Kotlin snippets accordingly.
- If you prefer to migrate fully to JUnit5, you can omit the Vintage engine and convert JUnit4 tests gradually.
