/* Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL. */

pub(crate) fn local_path(url: &str) -> Result<std::path::PathBuf, String> {
    let rest = url.strip_prefix("file://").ok_or("Invalid local analysis URL")?;
    let path = rest.strip_prefix("localhost/").map(|p| format!("/{p}"))
        .unwrap_or_else(|| rest.to_owned());
    if !path.starts_with('/') || path.starts_with("//") || path.contains(['?', '#', '\\']) {
        return Err("Invalid local analysis URL".into());
    }
    let mut decoded = Vec::new();
    let mut bytes = path.as_bytes().iter().copied();
    while let Some(byte) = bytes.next() {
        let value = if byte == b'%' {
            let high = bytes.next().and_then(|b| (b as char).to_digit(16));
            let low = bytes.next().and_then(|b| (b as char).to_digit(16));
            match (high, low) {
                (Some(h), Some(l)) => (h * 16 + l) as u8,
                _ => return Err("Invalid local analysis URL escape".into()),
            }
        } else { byte };
        if value == 0 { return Err("Invalid local analysis path".into()); }
        decoded.push(value);
    }
    let path = String::from_utf8(decoded).map_err(|_| "Invalid local analysis path")?;
    if path.starts_with("//") || path.contains('\\') { return Err("Invalid local analysis path".into()); }
    #[cfg(windows)]
    let path = path.strip_prefix('/').unwrap_or(&path).to_owned();
    let path = std::path::PathBuf::from(path);
    if !path.is_absolute() { return Err("Invalid local analysis path".into()); }
    Ok(path)
}


#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn local_urls_decode_escaped_paths_and_reject_remote_authorities() {
        let expected = if cfg!(windows) { "C:/Music/café song.flac" } else { "/Music/café song.flac" };
        let url = if cfg!(windows) { "file:///C:/Music/caf%C3%A9%20song.flac" } else { "file:///Music/caf%C3%A9%20song.flac" };
        assert_eq!(local_path(url).unwrap(), std::path::PathBuf::from(expected));
        for url in ["file://server/share/song", "file:relative", "file:///bad%", "file:///bad%00path", "file:///%2Fserver/share", "file:///%5C%5Cserver/share"] {
            assert!(local_path(url).is_err());
        }
    }

}
