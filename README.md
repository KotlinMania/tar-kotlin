# tar-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Ftar--kotlin-blue.svg)](https://github.com/KotlinMania/tar-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/tar-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/tar-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/tar-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/tar-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`alexcrichton/tar-rs`](https://github.com/alexcrichton/tar-rs).

**Original Project:** This port is based on [`alexcrichton/tar-rs`](https://github.com/alexcrichton/tar-rs). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `alexcrichton/tar-rs`

> The text below is reproduced and lightly edited from [`https://github.com/alexcrichton/tar-rs`](https://github.com/alexcrichton/tar-rs). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

## tar-rs

[Documentation](https://docs.rs/tar)

A tar archive reading/writing library for Rust.

```toml
# Cargo.toml
[dependencies]
tar = "0.4"
```

## Reading an archive

```rust,no_run
extern crate tar;

use std::io::prelude::*;
use std::fs::File;
use tar::Archive;

fn main() {
    let file = File::open("foo.tar").unwrap();
    let mut a = Archive::new(file);

    for file in a.entries().unwrap() {
        // Make sure there wasn't an I/O error
        let mut file = file.unwrap();

        // Inspect metadata about the file
        println!("{:?}", file.header().path().unwrap());
        println!("{}", file.header().size().unwrap());

        // files implement the Read trait
        let mut s = String::new();
        file.read_to_string(&mut s).unwrap();
        println!("{}", s);
    }
}

```

## Writing an archive

```rust,no_run
extern crate tar;

use std::io::prelude::*;
use std::fs::File;
use tar::Builder;

fn main() {
    let file = File::create("foo.tar").unwrap();
    let mut a = Builder::new(file);

    a.append_path("file1.txt").unwrap();
    a.append_file("file2.txt", &mut File::open("file3.txt").unwrap()).unwrap();
}
```

## License

This project is licensed under either of

 * Apache License, Version 2.0, ([LICENSE-APACHE](https://github.com/alexcrichton/tar-rs/blob/HEAD/LICENSE-APACHE) or
   https://www.apache.org/licenses/LICENSE-2.0)
 * MIT license ([LICENSE-MIT](https://github.com/alexcrichton/tar-rs/blob/HEAD/LICENSE-MIT) or
   https://opensource.org/license/mit)

at your option.

### Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in this project by you, as defined in the Apache-2.0 license,
shall be dual licensed as above, without any additional terms or conditions.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:tar-kotlin:0.1.0")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same MIT license as the upstream [`alexcrichton/tar-rs`](https://github.com/alexcrichton/tar-rs). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the tar-rs authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`alexcrichton/tar-rs`](https://github.com/alexcrichton/tar-rs) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
