import XCTest
import Tar

// Smoke test for the Kotlin → Swift Export → SPM → swift test pipeline.
//
// The file's mere existence and successful compilation prove three layers
// of the pipeline:
//
//   1. `embedSwiftExportForXcode` produced `Tar.swiftmodule/`
//      and the supporting KotlinRuntimeSupport / ExportedKotlinPackages /
//      KotlinRuntime swiftmodule bundles. If any of them were missing,
//      `import Tar` above would fail at compile time.
//
//   2. The static archive `libTar.a` (produced by the
//      `linkSwiftExportBinaryDebugStaticMacosArm64` and
//      `mergeMacosDebugSwiftExportLibraries` tasks) supplied every
//      `__root____*` and `KotlinError`-related symbol the Swift modules
//      reference. If the archive were missing or empty, this test
//      executable would fail to link with "undefined symbols for
//      architecture arm64".
//
//   3. The Kotlin `swiftExport { moduleName = "Tar" }` and
//      `flattenPackage = "io.github.kotlinmania.tar"` configuration in
//      build.gradle.kts produced a module name that's both syntactically
//      valid as a Swift identifier and reachable from this Package.swift
//      via the `TarLibrary` product.
final class TarExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        let header = Header.Companion.shared.newGnu()
        header.setSize(size: 1024)
        XCTAssertEqual(1024, header.entrySize())
    }
}
