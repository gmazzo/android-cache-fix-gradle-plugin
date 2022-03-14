package org.gradle.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.util.VersionNumber

@MultiVersionTest
class ConfigurationCachingTest extends AbstractTest {
    private static final VersionNumber SUPPORTED_KOTLIN_VERSION = TestVersions.latestSupportedKotlinVersion()

    def "plugin is compatible with configuration cache"() {
        given:
        SimpleAndroidApp.builder(temporaryFolder.root, cacheDir)
                .withAndroidVersion(TestVersions.latestAndroidVersionForCurrentJDK())
                .withKotlinVersion(SUPPORTED_KOTLIN_VERSION)
                .build()
                .writeProject()

        when:
        def result = withGradleVersion(TestVersions.latestGradleVersion().version)
                .withProjectDir(temporaryFolder.root)
                .withArguments('--configuration-cache', 'assembleDebug')
                .build()

        then:
        // In Gradle 7.4 the build always uses the following words.
        if (!result.output.contains("0 problems were found storing the configuration cache")) {
            !result.output.contains("problems were found storing the configuration cache")
        }

        when:
        result = withGradleVersion(TestVersions.latestGradleVersion().version)
            .withProjectDir(temporaryFolder.root)
            .withArguments('--configuration-cache', 'assembleDebug')
            .build()

        then:
        assertConfigurationCacheIsReused(result)
    }

    void assertConfigurationCacheIsReused(BuildResult result) {
        assert result.output.contains('Reusing configuration cache.')
    }
}
