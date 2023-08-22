# Changelog

## [Unreleased]

### Added

- Added a plugin config with various settings
- Deleted worlds are now backed up in the `fahare-backups` folder by default (configurable)
- Added a `/fahare reset` command to manually reset the world

### Changed

- Through the config, you can now disable the automatic reset feature in favor of the reset command

## [1.0.1] - 2023-08-07

### Added

- MIT license

### Changed

- Made more log messages translatable
- De-duplicated error logs
- Switched log messages to use the world short name instead of the key to match vanilla logs

## [1.0.0] - 2023-08-06

### Added

- Automatic world reset when all online players die
- Support for Paper 1.19.3+
- English translation

[unreleased]: https://github.com/qixils/fahare/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/qixils/fahare/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/qixils/fahare/releases/tag/v1.0.0