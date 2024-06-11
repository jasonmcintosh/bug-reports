# Gradle 6 to 7(or 8.8 with testing) transitive dep issue


### Summary
Gradle 6 let you override a dependency by adding a "force" on the dep.  Gradle 7+ you have to use a resolution strategy.  However, this resolution strategy does NOT seem to propagate to down stream repos.  This demonstrates this issue.

### How to verify:

* There are two branches involved
  * gradle-7-dep-failure-on-override - this has gradle 8 based syntax
  * gradle-7-dep-failure-on-override-when-using-gradle-6 - this has gradle 6 syntax.


You can verify the bug by doing the following:
```
 ./gradlew -q 'c:dependencyInsight'  --dependency google-api-services-compute --configuration runtimeClasspath
```

On veresion 6 of gradle, this shows the CORRECT "B" Override that forces a newer version.  NOTE if you use this on project B, on both gradle 6 AND 8 you'll see the "beta" version shown.  On gradle 6, running this
insight on project "C" will ALSO show beta as the slected version.

On version 7 and 8 of gradle, this is no longer the case.  There are two cases seen:
* Switching to the resolution strategy will suddenly change the version on project C to "alpha" potentiallY (and actually in spinnaker) causing breakages, and is a change in behavior above
* Switching to the the "stricly" command to FORCE a version... will cause a conflict with the enforcedPlatform and C will fail.

This seems like a significant potential breakgage as project B no longer has a way to say to "project C" that "USE THIS VERSION" and instead now when doing "stricly" things fail.  Arguably - they should have failed before.  
There also doesn't seem to be a way to "force" allow the version in B to replace the enforcedPlatform version.

