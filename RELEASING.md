# Datasyslab JTS release guide

Fork releases use `org.datasyslab` Maven coordinates and retain the
`org.locationtech.jts.*` Java packages. The first version is
`1.21.0-datasyslab-1`, based on upstream commit
`7e2b0e5d53fa411d6b58b8e5b395b1361f9711f8` from the `org.datasyslab` branch.
It includes upstream development changes after 1.20.0; it is not an official
upstream 1.21.0 release.

## Release contents

The initial publication contains these artifacts at the same version:

- `org.datasyslab:jts` (parent POM)
- `org.datasyslab:jts-modules` (parent POM)
- `org.datasyslab:jts-core` (binary, sources, Javadoc and test jars)

The `build-tools` module is built and installed locally for Checkstyle and PMD.
It is not a runtime dependency and is not deployed. IO, application and optional
database modules remain available for source builds but are not part of the
initial publication. The `-pl modules/core -am` selection below defines this scope.

## Prepare a version

1. Start from the `org.datasyslab` branch. Keep fixes in separate commits with
   regression tests, and record their upstream references in the release notes.
2. Set the same fork version in all active module POMs and `build-tools/pom.xml`.
   Keep the upstream version prefix and increment the `datasyslab-N` suffix.
3. Update `JTSVersion.RELEASE_INFO`, the README and this guide for that version.
4. Preserve the upstream license files, source headers, exported packages and
   module names in the full `jts-core` fork. Do not relocate or bundle another
   copy of JTS in that artifact. The narrowly scoped `jts-io-patch` module is the
   documented exception: it generates three IO classes in a separate namespace
   while retaining stock JTS geometry and support types.

## Build and test locally

Use Maven 3.9.2 or newer and JDK 17. The compiler still targets Java 8 bytecode.
Bootstrap the repository's build configuration, then run the full reactor:

```sh
mvn -B -f build-tools/pom.xml clean install
mvn -B clean install
```

Build the publication subset, including sources and Javadocs:

```sh
mvn -B -pl modules/core -am clean verify
```

These commands do not upload artifacts. Inspect the core jars in
`modules/core/target`, verify the fork version and module metadata, and test a
consumer against the built artifact. A plain build is unsigned and cannot be
submitted to Central as a complete release.

## Build the isolated IO patch

`org.datasyslab:jts-io-patch:1.21.0-datasyslab-1` is an independently built
artifact for applications that keep `org.locationtech.jts:jts-core:1.20.0` at
runtime. During `generate-sources`, it selects `WKBReader`, `WKTWriter`, and
package-private `CheckOrdinatesFilter` from the maintained core sources and
generates them under `org.datasyslab.jts.io`. Their source headers and references
to upstream geometry, `Ordinate`, `ParseException`, and stream types are retained.
It does not contain `WKBWriter` or any geometry classes.

Build and install only this artifact with JDK 17 (producing Java 8 bytecode):

```sh
mvn -B -f modules/io-patch/pom.xml clean verify
mvn -B -f modules/io-patch/pom.xml clean install
```

The binary, source, and Javadoc jars are written to `modules/io-patch/target`.
Inspect those files and test a consumer with stock JTS 1.20 before release.

## Configure signing and Central access

The publishing account must have access to the `org.datasyslab` namespace in
the [Central Portal](https://central.sonatype.com/). Configure a Portal user token
in Maven's user settings, using server ID `central`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_TOKEN_USERNAME}</username>
      <password>${env.CENTRAL_TOKEN_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

Keep credentials in local settings or CI secrets. Configure GPG with the release
signing key and make its public key discoverable as required by Central. The
Maven GPG plugin can use the local GPG agent; `-Dgpg.keyname=KEY_ID` selects a key.

Build and sign locally before uploading:

```sh
mvn -B -pl modules/core -am -Drelease clean verify
```

`verify` does not upload anything. Check the generated `.asc` signatures for
the POMs and attached artifacts. See the [Central Maven publishing guide](https://central.sonatype.org/publish/publish-portal-maven/)
and [artifact requirements](https://central.sonatype.org/publish/requirements/).

## Publish

After review, merge the fixes and release configuration into `org.datasyslab`.
Create and push an annotated tag matching the Maven version, for example
`1.21.0-datasyslab-1`. Build and publish from that exact tag:

```sh
mvn -B -f build-tools/pom.xml clean install
mvn -B -pl modules/core -am -Drelease clean deploy
```

**This command uploads and automatically publishes the release to Maven Central.**
The release profile uses the Central Publishing plugin and GPG signing. It waits
for Central to report publication. `autoPublish=false` would still upload;
neither that setting nor `skipPublishing` is a local bundle-building mode.

Verify the published POM parent chain and jars using a clean Maven repository,
then create release notes listing the upstream base and each included fix.
Central versions are immutable: use a new suffix for any subsequent correction.

The isolated IO patch has a separate publication scope. Its parent POMs already
exist at the same immutable version, so do not deploy the reactor or use `-am`.
Create the distinct annotated tag
`jts-io-patch-1.21.0-datasyslab-1` from the reviewed IO patch commit; do not move
or replace the existing full-core tag. After local verification and explicit
release approval, deploy only the module from that new tag:

```sh
mvn -B -f modules/io-patch/pom.xml -Drelease clean deploy
```

This command uploads and publishes the IO patch. Never rerun the full-core deploy
to publish it, because that would attempt to redeploy existing parent and core
coordinates.
