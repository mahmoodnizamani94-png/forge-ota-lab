use std::io::Result;

fn main() -> Result<()> {
    // WHY in-tree proto: We embed the AOSP update_metadata.proto rather than
    // fetching it at build time. This ensures deterministic builds and avoids
    // network dependencies during compilation.
    prost_build::compile_protos(
        &["../../proto/update_metadata.proto"],
        &["../../proto/"],
    )?;
    Ok(())
}
